package space.kscience.controls.constructor

import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import space.kscience.controls.api.CachingDevice
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.DeviceTreeChildDeviceChangedMessage
import space.kscience.controls.api.DeviceTreeMessage
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class LateBindingTest {
    private class ValueDevice(context: Context, initialValue: Double) : DeviceConstructor(context) {
        val value = registerVirtualProperty("value", initialValue, MetaConverter.double)
    }

    private class MutableTree : DeviceTree {
        override val device: Device? = null
        override val children: MutableMap<String, DeviceTree> = mutableMapOf()
        private val changes = MutableSharedFlow<DeviceTreeMessage>()
        override val treeMessageFlow: Flow<DeviceTreeMessage> get() = changes

        suspend fun install(name: String, child: DeviceTree) {
            children[name] = child
            changes.emit(DeviceTreeChildDeviceChangedMessage(Instant.DISTANT_PAST, name, Name.EMPTY))
        }
    }

    // becomes started when its messages are subscribed to, without sending a lifecycle message
    private class StartsOnSubscription(delegate: ValueDevice) : CachingDevice by delegate {
        private var state = LifecycleState.STOPPED
        override val lifecycleState: LifecycleState get() = state
        override val messageFlow: Flow<DeviceMessage> = MutableSharedFlow<DeviceMessage>().onSubscription {
            state = LifecycleState.STARTED
        }
    }

    private suspend fun TestScope.withDeviceContext(name: String, block: suspend TestScope.(Context) -> Unit) {
        val context = Context("late-binding-$name") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(DeviceManager)
        }
        try {
            block(context)
        } finally {
            withContext(NonCancellable) {
                withTimeout(5.seconds) { context.coroutineContext[Job]?.cancelAndJoin() }
                context.close()
            }
        }
    }

    @Test
    fun testChildInstalledIntoStartedConstructorIsBound() = runTest {
        withDeviceContext("constructor") { context ->
            val root = DeviceConstructor(context)
            root.start()
            val resolved = root.resolvePropertyState(Name.of("child"), "value")
            runCurrent()

            val child = ValueDevice(context, 1.0)
            root.installTree("child", child)
            runCurrent()

            assertEquals(1.0, resolved.value.double)
            child.value.value = 2.0
            assertEquals(2.0, resolved.value.double)
        }
    }

    @Test
    fun testStartedDeviceRegisteredLaterIsBound() = runTest {
        withDeviceContext("manager") { context ->
            val manager = context.request(DeviceManager)
            val child = ValueDevice(context, 3.0)
            child.start()
            runCurrent()

            val resolved = manager.resolvePropertyState(context, Name.of("child"), "value")
            manager.registerDevice("child", child)
            runCurrent()

            assertEquals(3.0, resolved.value.double)
        }
    }

    @Test
    fun testStartedLeafRegisteredInNestedManagerIsBound() = runTest {
        withDeviceContext("nested-root") { rootContext ->
            withDeviceContext("nested-group") { groupContext ->
                val root = rootContext.request(DeviceManager)
                val group = groupContext.request(DeviceManager)
                root.registerDeviceTree("group", group)
                val resolved = root.resolvePropertyState(rootContext, Name.of("group", "leaf"), "value")
                runCurrent()

                val leaf = ValueDevice(groupContext, 7.0)
                leaf.start()
                group.registerDevice("leaf", leaf)
                runCurrent()

                assertEquals(7.0, resolved.value.double)
            }
        }
    }

    @Test
    fun testDeviceStartedDuringSubscriptionIsBound() = runTest {
        withDeviceContext("started-during-subscription") { context ->
            val root = MutableTree()
            val resolved = root.resolvePropertyState(context, Name.of("child"), "value")
            runCurrent()

            root.install("child", DeviceTree(StartsOnSubscription(ValueDevice(context, 5.0))))
            runCurrent()

            assertEquals(5.0, resolved.value.double)
        }
    }

    @Test
    fun testReplacedGroupRestartsTheSearch() = runTest {
        withDeviceContext("replaced-group") { context ->
            val root = MutableTree()
            root.install("group", MutableTree())
            val resolved = root.resolvePropertyState(context, Name.of("group", "leaf"), "value")
            runCurrent()

            val leaf = ValueDevice(context, 9.0)
            leaf.start()
            root.install("group", MutableTree().apply { children["leaf"] = DeviceTree(leaf) })
            runCurrent()

            assertEquals(9.0, resolved.value.double)
        }
    }
}
