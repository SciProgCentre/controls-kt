package space.kscience.controls.tagtable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.eclipse.milo.opcua.sdk.server.AddressSpace
import org.eclipse.milo.opcua.sdk.server.ManagedNamespace
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.items.DataItem
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId
import org.junit.jupiter.api.Timeout
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.long
import space.kscience.dataforge.meta.string
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class PlcOpcProvenanceTest {
    private class ReadNamespace(
        server: OpcUaServer,
    ) : ManagedNamespace(server, "urn:controls:test:table-reads"), AutoCloseable {

        var value = DataValue(Variant(7.0), StatusCode.BAD)

        override fun read(
            context: AddressSpace.ReadContext,
            maxAge: Double,
            timestamps: TimestampsToReturn,
            ids: List<ReadValueId>,
        ): List<DataValue> = ids.map { id ->
            if (id.nodeId == NodeId(namespaceIndex, "value") && id.attributeId == AttributeId.Value.uid()) {
                value
            } else {
                super.read(context, maxAge, timestamps, listOf(id)).single()
            }
        }

        override fun onDataItemsCreated(items: List<DataItem>) = Unit
        override fun onDataItemsModified(items: List<DataItem>) = Unit
        override fun onDataItemsDeleted(items: List<DataItem>) = Unit
        override fun onMonitoringModeChanged(items: List<MonitoredItem>) = Unit

        override fun close() {
            // do nothing
        }
    }

    @Test
    @Timeout(10)
    fun testQualityReading() = runBlocking {
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

        server.startup().await()
        val namespace = ReadNamespace(server)

        server.addressSpaceManager.register(namespace)
        val column = TagTableColumn.opc(
            source = "server",
            timer = "scan",
            nodeId = NodeId(namespace.namespaceIndex, "value").toParseableString(),
            removeAuxiliaryValues = false // keep opc data
        )
        val table = PlcTagTable(
            context, TagTableConfiguration(
                sources = mapOf("server" to OpcTagSourceConfig("opc.tcp://127.0.0.1:$port")),
                timers = mapOf("scan" to FixedRateTimer(20.milliseconds)),
                tags = mapOf("sensor" to column)
            )
        )
        table.start()

        delay(1.seconds)

        for ((index, status) in listOf(StatusCode.BAD, StatusCode.UNCERTAIN).withIndex()) {
            namespace.value = DataValue(
                /* value = */ Variant(index.toDouble()),
                /* status = */ status,
                /* sourceTime = */ DateTime(Instant.parse("2026-09-21T10:00:00Z").toJavaInstant()),
                /* serverTime = */ DateTime(Instant.parse("2026-09-21T10:00:1${index}Z").toJavaInstant()),
            )

            delay(100.milliseconds)
            val tagValueWithTime = table.readTagWithTime("sensor")
            if (status.isGood) {
                assertEquals(TagState.GOOD_QUALITY, table.readTagQuality("sensor").quality)
            } else {
                assertEquals(TagState.BAD_QUALITY, table.readTagQuality("sensor").quality)
            }
            assertEquals(index.toDouble(), tagValueWithTime.value.double)
            assertEquals(status.value, tagValueWithTime.value["@opc.status"].long)
            assertEquals(
                "2026-09-21T10:00:00Z",
                tagValueWithTime.value["@opc.sourceTime"].string
            )
            assertEquals(tagValueWithTime.time.toString(), tagValueWithTime.value["@opc.serverTime"].string)

        }

        namespace.close()
        table.stop()
        server.shutdown().await()
        context.close()

    }
}
