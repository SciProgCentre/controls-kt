package space.kscience.controls.tagtable

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DeviceLifeCycleMessage
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.tagtable.storage.ReplayTagTable
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.IOPlugin
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplayTagTableTest {
    @Test
    fun testLifecycle() = runTest {
        val context = Context("replay-lifecycle") {
            plugin(IOPlugin)
            plugin(ControlsStoragePlugin)
        }
        val directory = Files.createTempDirectory("replay-lifecycle-test")
        try {
            val index = TableStorageIndex(context.request(ControlsStoragePlugin), directory)
            val table = ReplayTagTable(index, emptyMap())
            assertEquals(LifecycleState.STOPPED, table.lifecycleState)

            val states = async(start = CoroutineStart.UNDISPATCHED) {
                table.messageFlow.filterIsInstance<DeviceLifeCycleMessage>()
                    .map { it.state }.take(3).toList()
            }

            try {
                table.start()
                assertEquals(LifecycleState.STARTED, table.lifecycleState)
            } finally {
                table.stop()
            }
            assertEquals(LifecycleState.STOPPED, table.lifecycleState)
            assertEquals(
                listOf(LifecycleState.STARTING, LifecycleState.STARTED, LifecycleState.STOPPED),
                states.await(),
            )
        } finally {
            context.close()
            directory.toFile().deleteRecursively()
        }
    }
}
