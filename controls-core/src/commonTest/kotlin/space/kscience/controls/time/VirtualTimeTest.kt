package space.kscience.controls.time

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private data class TimedResult(val time: Instant, val marker: String)

class VirtualTimeTest {
    @Test
    fun manualAdvance() = runTest {
        val timeManager = VirtualTimeManager(Instant.fromEpochMilliseconds(0L))
        val collector = mutableListOf<TimedResult>()
        launch(Dispatchers.Default) {
            withTimeout(500) {
                repeat(3) { series ->
                    launch {
                        timeManager.advanceTimeBy(series, 100.milliseconds * (series + 1))
                        repeat(10) { number ->
                            collector.add(TimedResult(timeManager.now(),"$series.$number"))
                            timeManager.advanceTimeBy(series, 2000.milliseconds)
                        }
                        timeManager.pass(series)
                    }
                }
            }
        }
        println(collector.joinToString("\n"))
        assertTrue { collector.sortedBy { it.time } == collector }
    }

    @Test
    fun contextAdvance() = runTest {
        val timeManager = VirtualTimeManager(Instant.fromEpochMilliseconds(0L))
        val collector = mutableListOf<TimedResult>()
        launch (Dispatchers.Default.withVirtualTime(timeManager)) {
            withTimeout(500) {
                repeat(3) { series ->
                    launch {
                        delay(100.milliseconds * (series + 1))
                        repeat((series + 1) * 10) { number ->
                            collector.add(TimedResult(timeManager.now(),"$series.$number"))
                            println(collector.last())
                            delay(2000.milliseconds)
                        }
                        //timeManager.pass(this)
                    }
                }
                launch {
                    delay(30.seconds)
                    collector.add(TimedResult(timeManager.now(),"side"))
                    println("Complete")
                }
            }
        }
//        println(collector.joinToString("\n"))
        assertTrue { collector.sortedBy { it.time } == collector }
    }
}