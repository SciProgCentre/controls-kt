@file:OptIn(ExperimentalCoroutinesApi::class)

package space.kscience.controls.client

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Instant

internal class DeviceClientMessageTest {
    @Test
    fun testDeviceMessagesUseLocalNames() = runTest {
        val context = Context("device-client-names") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        for (remoteName in listOf(Name.EMPTY, Name.of("child"), Name.of("group", "child"))) {
            val incoming = MutableSharedFlow<DeviceMessage>()
            val client = DeviceClient(context, remoteName, emptyList(), emptyList(), incoming) {}
            val direct = mutableListOf<DeviceMessage>()
            val mounted = mutableListOf<DeviceMessage>()
            val tree = DeviceTree(children = mapOf("child" to DeviceTree(client)))
            val directJob = backgroundScope.launch { client.messageFlow.collect { direct.add(it) } }
            val treeJob = backgroundScope.launch { tree.deviceMessageFlow().collect { mounted.add(it) } }
            runCurrent()

            val event = PropertyChangedMessage(
                time = Instant.fromEpochMilliseconds(123),
                property = "value",
                value = Meta.EMPTY,
                sourceDevice = remoteName,
                targetDevice = Name.of("receiver"),
                comment = "sample",
            )
            incoming.emit(event.copy(sourceDevice = Name.of("unrelated")))
            incoming.emit(event)
            runCurrent()

            assertEquals(listOf<DeviceMessage>(event.copy(sourceDevice = Name.of("child"))), mounted)
            assertEquals(listOf<DeviceMessage>(event.copy(sourceDevice = Name.EMPTY)), direct)
            assertSame(event.value, client.getCachedProperty("value"))
            if (remoteName == Name.EMPTY) assertSame(event, direct.single())
            directJob.cancel()
            treeJob.cancel()
        }

        context.close()
    }

    @Test
    fun testRemoteHubPrefixesChildOnce() = runTest {
        val context = Context("remote-hub-source") {
            coroutineContext(backgroundScope.coroutineContext)
        }

        val incoming = MutableSharedFlow<MagixMessage>()
        val endpoint = object : MagixEndpoint {
            override fun subscribe(filter: MagixMessageFilter): Flow<MagixMessage> = incoming
            override suspend fun broadcast(message: MagixMessage) {}
            override fun close() {}
        }

        suspend fun emit(message: DeviceMessage) {
            incoming.emit(
                MagixMessage(
                    format = DeviceManager.magixFormat.defaultFormat,
                    payload = MagixEndpoint.magixJson.encodeToJsonElement(
                        DeviceManager.magixFormat.serializer, message
                    ),
                    sourceEndpoint = "device",
                )
            )
        }

        val hub = endpoint.remoteDeviceTree(context, "client", "device")
        runCurrent()
        val name = Name.of("child")
        emit(DescriptionMessage(Instant.fromEpochMilliseconds(0), Meta.EMPTY, emptyList(), emptyList(), name))
        runCurrent()
        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch { hub.deviceMessageFlow().collect { received.add(it) } }
        runCurrent()

        val event = PropertyChangedMessage(Instant.fromEpochMilliseconds(1), "value", Meta.EMPTY, name)
        emit(event)
        runCurrent()
        assertEquals(listOf<DeviceMessage>(event), received)

        context.close()
    }

    private class TestEndpoint : MagixEndpoint {
        val incoming = MutableSharedFlow<MagixMessage>()
        override fun subscribe(filter: MagixMessageFilter): Flow<MagixMessage> = incoming
        override suspend fun broadcast(message: MagixMessage) {}
        override fun close() {}

        suspend fun emit(message: DeviceMessage) {
            incoming.emit(
                MagixMessage(
                    format = DeviceManager.magixFormat.defaultFormat,
                    payload = MagixEndpoint.magixJson.encodeToJsonElement(
                        DeviceManager.magixFormat.serializer, message
                    ),
                    sourceEndpoint = "device",
                )
            )
        }
    }

    @Test
    fun testRemoteHubObservesDeviceDescribedAfterCollectionStarts() = runTest {
        for (name in listOf(Name.EMPTY, Name.of("child"))) {
            val context = Context("remote-hub-late-description") {
                coroutineContext(backgroundScope.coroutineContext)
            }
            val endpoint = TestEndpoint()
            val hub = endpoint.remoteDeviceTree(context, "client", "device")
            val received = mutableListOf<DeviceMessage>()
            val collector = backgroundScope.launch { hub.deviceMessageFlow().collect { received.add(it) } }
            runCurrent()
            endpoint.emit(DescriptionMessage(Instant.fromEpochMilliseconds(0), Meta.EMPTY, emptyList(), emptyList(), name))
            runCurrent()

            val event = PropertyChangedMessage(Instant.fromEpochMilliseconds(1), "value", Meta.EMPTY, name)
            endpoint.emit(event)
            runCurrent()
            assertEquals(listOf<DeviceMessage>(event), received, "$name")
            collector.cancel()
            context.close()
        }
    }

    @Test
    fun testRemoteTreeMessagesAreNotLocalChanges() = runTest {
        val context = Context("remote-hub-remote-tree-messages") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        val endpoint = TestEndpoint()
        val hub = endpoint.remoteDeviceTree(context, "client", "device")
        runCurrent()
        val name = Name.of("child")
        endpoint.emit(DescriptionMessage(Instant.fromEpochMilliseconds(0), Meta.EMPTY, emptyList(), emptyList(), name))
        runCurrent()

        val changes = mutableListOf<DeviceTreeMessage>()
        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch { hub.treeMessageFlow.collect { changes.add(it) } }
        backgroundScope.launch { hub.deviceMessageFlow().collect { received.add(it) } }
        runCurrent()
        // composition changes on the remote side do not change the devices known to this client
        val time = Instant.fromEpochMilliseconds(1)
        endpoint.emit(DeviceTreeChildDeviceChangedMessage(time, "child", Name.of("group")))
        endpoint.emit(DeviceTreeRootDeviceChangedMessage(time, Name.EMPTY))
        runCurrent()

        val event = PropertyChangedMessage(Instant.fromEpochMilliseconds(2), "value", Meta.EMPTY, name)
        endpoint.emit(event)
        runCurrent()
        assertEquals(emptyList(), changes)
        assertEquals(listOf<DeviceMessage>(event), received.filterIsInstance<PropertyChangedMessage>())
        context.close()
    }

    @Test
    fun testRequestsKeepRemoteNames() = runTest {
        val context = Context("device-client-requests") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        val name = Name.of("group", "child")
        val incoming = MutableSharedFlow<DeviceMessage>()
        val sent = mutableListOf<DeviceMessage>()
        val client = DeviceClient(context, name, emptyList(), emptyList(), incoming) { sent.add(it) }
        val read = backgroundScope.async { client.readProperty("value") }
        runCurrent()
        assertEquals(name, assertIs<PropertyGetMessage>(sent.last()).targetDevice)
        incoming.emit(PropertyChangedMessage(Instant.fromEpochMilliseconds(123), "value", Meta.EMPTY, name))
        runCurrent()
        assertEquals(Meta.EMPTY, read.await())

        client.writeProperty("value", Meta.EMPTY)
        assertEquals(name, assertIs<PropertySetMessage>(sent.last()).targetDevice)
        val execute = backgroundScope.async { client.execute("reset", Meta.EMPTY) }
        runCurrent()
        val request = assertIs<ActionExecuteMessage>(sent.last())
        assertEquals(name, request.targetDevice)
        incoming.emit(
            ActionResultMessage(
                Instant.fromEpochMilliseconds(124), "reset", Meta.EMPTY, request.requestId, name,
            )
        )
        runCurrent()
        assertEquals(Meta.EMPTY, execute.await())
        context.close()
    }
}
