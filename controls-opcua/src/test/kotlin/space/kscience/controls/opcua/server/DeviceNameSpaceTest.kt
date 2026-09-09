package space.kscience.controls.opcua.server

import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode
import org.junit.jupiter.api.Test
import space.kscience.controls.api.*
import space.kscience.controls.spec.InternalDeviceAPI
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.asValue
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class DeviceNameSpaceTest {
    @Test
    fun testPropertyMessageConversion() {
        val time = Instant.parse("2026-09-08T12:00:00.123456700Z")
        val message = PropertyChangedMessage(time, "value", Meta(1.0.asValue()))
        val timed = message.toOpc()
        assertEquals(1.0, timed.value.value)
        assertEquals(StatusCode.GOOD, timed.statusCode)
        assertEquals(time.toJavaInstant(), assertNotNull(timed.sourceTime).javaInstant)
        assertTrue(assertNotNull(timed.serverTime).isValid)

        val unknownTime = message.copy(time = Instant.DISTANT_PAST).toOpc()
        assertEquals(timed.value, unknownTime.value)
        assertEquals(timed.statusCode, unknownTime.statusCode)
        assertNull(unknownTime.sourceTime)
        assertTrue(assertNotNull(unknownTime.serverTime).isValid)
    }

    private class TestDevice(override val context: Context) : CachingDevice {
        override val coroutineContext = context.coroutineContext
        override val clock = Clock.System
        override val lifecycleState = LifecycleState.STOPPED
        override val messageFlow = MutableSharedFlow<DeviceMessage>()
        override val propertyDescriptors = listOf(PropertyDescriptor("value").apply { valueType(ValueType.NUMBER) })
        override val actionDescriptors = emptyList<ActionDescriptor>()
        private var cachedValue: Meta? = Meta(0.0.asValue())

        override fun getCachedProperty(propertyName: String): Meta? = cachedValue

        @InternalDeviceAPI
        override fun setCachedProperty(propertyName: String, value: Meta?) {
            cachedValue = value
        }

        override suspend fun readProperty(propertyName: String): Meta = checkNotNull(cachedValue)
        override suspend fun writeProperty(propertyName: String, value: Meta): Unit = error("Read-only property")
        override suspend fun execute(actionName: String, argument: Meta?): Meta? = error("No actions")
    }

    @Test
    fun testPropertyUpdatesPreserveSourceTime() = runTest {
        val context = Context("opc-property-time") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val device = TestDevice(context)
            val server = OpcUaServer {
                endpoint {
                    setBindAddress("127.0.0.1")
                    setHostname("127.0.0.1")
                    setBindPort(0)
                }
            }
            var serverStarted = false
            try {
                server.startup().await()
                serverStarted = true
                val tree = DeviceTree(children = mapOf("sensor" to DeviceTree(device)))
                val namespace = DeviceNameSpace(context, server, tree)
                try {
                    namespace.startup()
                    val node = assertIs<UaVariableNode>(
                        namespace.nodeManager.getNode(NodeId(namespace.namespaceIndex, "sensor/value")).orElseThrow()
                    )
                    assertEquals(0.0, node.value.value.value)
                    assertNull(node.value.sourceTime)

                    val updates = Channel<DataValue>(Channel.UNLIMITED)
                    node.addAttributeObserver { _, attributeId, value ->
                        if (attributeId == AttributeId.Value) updates.trySend(value as DataValue)
                    }
                    withTimeout(5.seconds) { device.messageFlow.subscriptionCount.first { it > 0 } }

                    device.messageFlow.emit(PropertyChangedMessage(Instant.DISTANT_PAST, "value", Meta(1.0.asValue())))
                    val unknownTime = withTimeout(5.seconds) { updates.receive() }
                    assertEquals(1.0, unknownTime.value.value)
                    assertEquals(StatusCode.GOOD, unknownTime.statusCode)
                    assertNull(unknownTime.sourceTime)
                    assertTrue(assertNotNull(unknownTime.serverTime).isValid)

                    val time = Instant.parse("2026-09-08T12:00:00.123456700Z")
                    device.messageFlow.emit(PropertyChangedMessage(time, "value", Meta(2.0.asValue())))
                    val timed = withTimeout(5.seconds) { updates.receive() }
                    assertEquals(2.0, timed.value.value)
                    assertEquals(StatusCode.GOOD, timed.statusCode)
                    assertEquals(time.toJavaInstant(), assertNotNull(timed.sourceTime).javaInstant)
                    assertTrue(assertNotNull(timed.serverTime).isValid)
                } finally {
                    namespace.shutdown()
                }
            } finally {
                if (serverStarted) server.shutdown().await()
            }
        } finally {
            context.cancel()
            context.close()
        }
    }
}
