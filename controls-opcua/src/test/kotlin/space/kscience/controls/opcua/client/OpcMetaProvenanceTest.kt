package space.kscience.controls.opcua.client

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.server.AddressSpace
import org.eclipse.milo.opcua.sdk.server.ManagedNamespace
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.items.DataItem
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.opcua.server.fromOpc
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.get
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

class OpcMetaProvenanceTest {
    private class ReadNamespace(server: OpcUaServer) : ManagedNamespace(server, "urn:controls:test:meta-reads") {
        @Volatile
        var samples: Map<NodeId, DataValue> = emptyMap()

        override fun read(
            context: AddressSpace.ReadContext,
            maxAge: Double,
            timestamps: TimestampsToReturn,
            ids: List<ReadValueId>,
        ): List<DataValue> = ids.map {
            samples[it.nodeId]?.takeIf { _ -> it.attributeId == AttributeId.Value.uid() }
                ?: super.read(context, maxAge, timestamps, listOf(it)).single()
        }

        override fun onDataItemsCreated(items: List<DataItem>) = Unit
        override fun onDataItemsModified(items: List<DataItem>) = Unit
        override fun onDataItemsDeleted(items: List<DataItem>) = Unit
        override fun onMonitoringModeChanged(items: List<MonitoredItem>) = Unit
    }

    private class RecordingClient(private val delegate: OpcUaClient) : OpcUaClient(delegate.config, delegate.transport) {
        var timestamps: TimestampsToReturn? = null
        var reply: List<DataValue> = emptyList()
        var controlledReply: List<DataValue>? = null

        override fun readValuesAsync(
            maxAge: Double,
            timestamps: TimestampsToReturn,
            nodeIds: List<NodeId>,
        ): CompletableFuture<List<DataValue>> {
            this.timestamps = timestamps
            val response = controlledReply?.let { CompletableFuture.completedFuture(it) }
                ?: delegate.readValuesAsync(maxAge, timestamps, nodeIds)
            return response.thenApply { reply = it; it }
        }
    }

    private suspend fun withClient(block: suspend (RecordingClient, ReadNamespace) -> Unit) {
        val port = ServerSocket(0).use { it.localPort }
        val server = OpcUaServer {
            endpoint {
                setBindAddress("127.0.0.1")
                setHostname("127.0.0.1")
                setBindPort(port)
            }
        }
        try {
            server.startup().await()
            val namespace = ReadNamespace(server)
            var registered = false
            try {
                server.addressSpaceManager.register(namespace)
                registered = true
                val client = OpcUaClient.create("opc.tcp://127.0.0.1:$port")
                try {
                    client.connectAsync().await()
                    withTimeout(15.seconds) { block(RecordingClient(client), namespace) }
                } finally {
                    client.disconnectAsync().await()
                }
            } finally {
                if (registered) server.addressSpaceManager.unregister(namespace)
            }
        } finally {
            server.shutdown().await()
        }
    }

    private fun assertSample(expected: DataValue, actual: ValueWithTime<Meta>) {
        assertEquals(Meta.fromOpc(expected), actual.value)
        assertEquals(assertNotNull(expected.serverTime).javaInstant.toKotlinInstant(), actual.time)
        expected.sourceTime?.let { assertNotEquals(it.javaInstant.toKotlinInstant(), actual.time) }
    }

    @Test
    fun singleAndBatchReadsPreserveProvenanceAndServerClock() = runBlocking {
        withClient { client, namespace ->
            val first = NodeId(namespace.namespaceIndex, "first")
            val second = NodeId(namespace.namespaceIndex, "second")
            val sourceTime = DateTime(Instant.parse("2026-09-21T10:00:00Z").toJavaInstant())
            val serverTime = DateTime(Instant.parse("2026-09-21T10:00:10Z").toJavaInstant())
            for (status in listOf(StatusCode.GOOD, StatusCode.BAD, StatusCode.UNCERTAIN)) {
                namespace.samples = mapOf(
                    first to DataValue(Variant(7.0), status, sourceTime, ushort(123), serverTime, ushort(456)),
                    second to DataValue(Variant(true), status, null, null, serverTime, null),
                )
                val single = client.readMetaWithTime(first, 0.0)
                assertEquals(TimestampsToReturn.Both, client.timestamps)
                assertEquals(status, client.reply.single().statusCode)
                assertEquals(sourceTime, client.reply.single().sourceTime)
                assertSample(client.reply.single(), single)
                assertEquals(7.0, MetaConverter.double.read(single.value))

                val batch = client.readMultipleMetaWithTime(listOf(second, first), 0.0)
                assertEquals(TimestampsToReturn.Both, client.timestamps)
                assertEquals(2, batch.size)
                client.reply.zip(batch).forEach { (expected, actual) -> assertSample(expected, actual) }
                client.reply.forEach { assertEquals(status, it.statusCode) }
                assertEquals(true, MetaConverter.boolean.read(batch.first().value))
            }
        }
    }

    @Test
    fun nullSourceTimeDoesNotUseServerTimeAsFallback() = runBlocking {
        withClient { client, namespace ->
            val node = NodeId(namespace.namespaceIndex, "no-source-time")
            val serverTime = DateTime(Instant.parse("2026-09-21T10:00:10Z").toJavaInstant())
            val response = DataValue(Variant("sample"), StatusCode.UNCERTAIN, null, serverTime)
            client.controlledReply = listOf(response)
            val samples = listOf(
                client.readMetaWithTime(node, 0.0),
                client.readMultipleMetaWithTime(listOf(node), 0.0).single(),
            )
            samples.forEach {
                assertSample(response, it)
                assertEquals("sample", MetaConverter.string.read(it.value))
                assertNull(it.value["@opc.sourceTime"])
            }
        }
    }

    @Test
    fun missingServerTimeStillFailsWithoutSourceFallback() = runBlocking {
        withClient { client, namespace ->
            val node = NodeId(namespace.namespaceIndex, "missing-server-time")
            client.controlledReply = listOf(DataValue(
                Variant(7.0), StatusCode.GOOD,
                DateTime(Instant.parse("2026-09-21T10:00:00Z").toJavaInstant()), null,
            ))
            assertEquals("No server time provided", assertFailsWith<IllegalStateException> {
                client.readMetaWithTime(node, 0.0)
            }.message)
            assertEquals("No server time provided", assertFailsWith<IllegalStateException> {
                client.readMultipleMetaWithTime(listOf(node), 0.0)
            }.message)
        }
    }
}
