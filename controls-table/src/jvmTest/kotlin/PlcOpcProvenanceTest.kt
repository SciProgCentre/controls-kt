package space.kscience.controls.tagtable

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.future.await
import org.eclipse.milo.opcua.sdk.server.AddressSpace
import org.eclipse.milo.opcua.sdk.server.ManagedNamespace
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.items.DataItem
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.long
import space.kscience.dataforge.meta.string
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

class PlcOpcProvenanceTest {
    private class ReadNamespace(server: OpcUaServer) : ManagedNamespace(server, "urn:controls:test:table-reads") {
        @Volatile
        var sample = DataValue(Variant(7.0), StatusCode.BAD)
        val permits = Channel<Unit>(Channel.UNLIMITED)
        val replies = Channel<DataValue>(Channel.UNLIMITED)

        override fun read(
            context: AddressSpace.ReadContext,
            maxAge: Double,
            timestamps: TimestampsToReturn,
            ids: List<ReadValueId>,
        ): List<DataValue> {
            return ids.map { id ->
                if (id.nodeId == NodeId(namespaceIndex, "value") && id.attributeId == AttributeId.Value.uid()) {
                    val permitted = runBlocking { withTimeout(15.seconds) { permits.receiveCatching().isSuccess } }
                    if (!permitted) return@map DataValue(StatusCode.BAD)
                    sample.also { replies.trySend(it).getOrThrow() }
                } else {
                    super.read(context, maxAge, timestamps, listOf(id)).single()
                }
            }
        }

        override fun onDataItemsCreated(items: List<DataItem>) = Unit
        override fun onDataItemsModified(items: List<DataItem>) = Unit
        override fun onDataItemsDeleted(items: List<DataItem>) = Unit
        override fun onMonitoringModeChanged(items: List<MonitoredItem>) = Unit
    }

    @Test
    fun batchReadsPublishQualityOnlyChangesWithTheirServerTime() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = OpcUaServer {
            endpoint {
                setBindAddress("127.0.0.1")
                setHostname("127.0.0.1")
                setBindPort(port)
            }
        }
        val context = Context("opc-table-provenance") {
            coroutineContext(SupervisorJob() + Dispatchers.Default)
            plugin(ClockManager)
        }
        var table: PlcTagTable? = null
        var namespaceToClose: ReadNamespace? = null
        var registered = false
        try {
            server.startup().await()
            val namespace = ReadNamespace(server)
            namespaceToClose = namespace
            server.addressSpaceManager.register(namespace)
            registered = true
            val column = OpcTagTableColumn(
                source = "server", timer = "scan",
                nodeId = NodeId(namespace.namespaceIndex, "value").toParseableString(),
            )
            val createdTable = PlcTagTable(context, TagTableConfiguration(
                sources = mapOf("server" to OpcUaConfig("opc.tcp://127.0.0.1:$port")),
                timers = mapOf("scan" to FixedRateTimer(20.milliseconds)),
                properties = mapOf("sensor" to column),
            ))
            table = createdTable
            // Establish the session before the table's one-second polling timeout applies.
            namespace.permits.send(Unit)
            createdTable.read(column)
            namespace.replies.receive()
            val messages = Channel<PropertyChangedMessage>(Channel.UNLIMITED)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                createdTable.messageFlow.filterIsInstance<PropertyChangedMessage>().collect {
                    if (it.property == "sensor") messages.send(it)
                }
            }
            try {
                createdTable.start()
                for ((index, status) in listOf(StatusCode.BAD, StatusCode.UNCERTAIN).withIndex()) {
                    namespace.sample = DataValue(
                        Variant(7.0), status,
                        DateTime(Instant.parse("2026-09-21T10:00:00Z").toJavaInstant()),
                        DateTime(Instant.parse("2026-09-21T10:00:1${index}Z").toJavaInstant()),
                    )
                    // The server cannot capture the next DataValue until this permit is released.
                    namespace.permits.send(Unit)
                    withTimeout(20.seconds) {
                        val reply = namespace.replies.receive()
                        assertEquals(status, reply.statusCode)
                        val message = messages.receive()
                        assertEquals(7.0, MetaConverter.double.read(message.value))
                        assertEquals(status.value, message.value["@opc.status"].long)
                        assertEquals(assertNotNull(reply.sourceTime).javaInstant.toKotlinInstant().toString(),
                            message.value["@opc.sourceTime"].string)
                        assertEquals(message.time.toString(), message.value["@opc.serverTime"].string)
                        assertEquals(message.value, createdTable.readWithTime("sensor").value)
                    }
                }
            } finally {
                collector.cancelAndJoin()
            }
        } finally {
            namespaceToClose?.permits?.cancel()
            try {
                try {
                    table?.stop()
                } finally {
                    table?.coroutineContext?.get(Job)?.cancelAndJoin()
                }
            } finally {
                try {
                    if (registered) server.addressSpaceManager.unregister(checkNotNull(namespaceToClose))
                } finally {
                    try {
                        server.shutdown().await()
                    } finally {
                        context.cancel()
                        context.close()
                    }
                }
            }
        }
    }
}
