@file:OptIn(ExperimentalCoroutinesApi::class)

package space.kscience.controls.client

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DescriptionMessage
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.messageFlow
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.time.Instant

internal class RemoteDeviceHubFlowTest {
    private class TestEndpoint : MagixEndpoint {
        private val incoming = MutableSharedFlow<MagixMessage>()

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

    private fun description(name: Name, properties: Collection<PropertyDescriptor> = emptyList()): DescriptionMessage =
        DescriptionMessage(
            time = Instant.fromEpochMilliseconds(0),
            description = Meta.EMPTY,
            properties = properties,
            actions = emptyList(),
            sourceDevice = name,
        )

    private fun message(name: Name, property: String): PropertyChangedMessage = PropertyChangedMessage(
        time = Instant.fromEpochMilliseconds(0),
        property = property,
        value = Meta.EMPTY,
        sourceDevice = name,
    )

    @Test
    fun testMessagesFromLateChildAndRoot() = runTest {
        val context = Context("remote-hub-late-devices") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val endpoint = TestEndpoint()
            val hub = endpoint.remoteDeviceHub(context, "client", "device")
            val received = mutableListOf<PropertyChangedMessage>()
            backgroundScope.launch {
                hub.messageFlow().filterIsInstance<PropertyChangedMessage>().collect { received.add(it) }
            }
            runCurrent()

            val childName = Name.of("child")
            endpoint.emit(description(childName))
            runCurrent()
            endpoint.emit(message(childName, "child-first"))
            runCurrent()

            endpoint.emit(description(Name.EMPTY))
            runCurrent()
            endpoint.emit(message(Name.EMPTY, "root"))
            runCurrent()
            endpoint.emit(message(childName, "child-second"))
            runCurrent()

            assertNotNull(hub.device)
            assertEquals(listOf("child-first", "root", "child-second"), received.map { it.property })
            assertEquals(Name.EMPTY, received.single { it.property == "root" }.sourceDevice)
        } finally {
            context.close()
        }
    }

    @Test
    fun testRepeatedDescriptionsPreserveChildIdentityAndSnapshots() = runTest {
        val context = Context("remote-hub-child-snapshots") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val endpoint = TestEndpoint()
            val hub = endpoint.remoteDeviceHub(context, "client", "device")
            val snapshots = mutableListOf<Map<String, DeviceTree>>()
            backgroundScope.launch { hub.childrenFlow().collect { snapshots.add(it) } }
            runCurrent()

            endpoint.emit(description(Name.of("first")))
            runCurrent()
            val firstSnapshot = snapshots.last()
            val firstChild = assertNotNull(firstSnapshot["first"])
            assertSame(firstChild, hub.children["first"])

            endpoint.emit(description(Name.of("first"), listOf(PropertyDescriptor("updated"))))
            runCurrent()
            assertEquals(2, snapshots.size)
            assertSame(firstChild, hub.children["first"])
            assertEquals("updated", firstChild.device?.propertyDescriptors?.single()?.name)

            endpoint.emit(description(Name.of("second")))
            runCurrent()
            assertEquals(3, snapshots.size)
            assertEquals(setOf("first"), firstSnapshot.keys)
            assertEquals(setOf("first", "second"), snapshots.last().keys)
            assertSame(firstChild, snapshots.last()["first"])
        } finally {
            context.close()
        }
    }
}
