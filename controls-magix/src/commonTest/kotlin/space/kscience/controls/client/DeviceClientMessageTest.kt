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
        try {
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
        } finally {
            context.close()
        }
    }

    @Test
    fun testRemoteHubPrefixesChildOnce() = runTest {
        val context = Context("remote-hub-source") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
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
        } finally {
            context.close()
        }
    }

    @Test
    fun testRequestsKeepRemoteNames() = runTest {
        val context = Context("device-client-requests") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
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
        } finally {
            context.close()
        }
    }
}
