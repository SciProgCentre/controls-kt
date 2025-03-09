package space.kscience.controls.constructor

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import space.kscience.controls.manager.ClockManager
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.context.request
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class TimerTest {

    @Test
    fun timer() = runTest {
        val timer = TimerState(Global.request(ClockManager), 10.milliseconds)
        timer.valueFlow.take(100).onEach {
            println(it)
        }.collect()

    }
}