package space.kscience.controls.time

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private data class TimedResult(val time: Instant, val marker: String)

class VirtualTimeTest {
    @Test
    fun manualAdvance() = runTest(timeout = 500.milliseconds) {
        val scheduler = VirtualTimeScheduler()//VirtualTimeManager(Instant.fromEpochMilliseconds(0L))
        val clock = scheduler.asClock(Instant.fromEpochMilliseconds(0L))
        val collector = mutableListOf<TimedResult>()
        launch(Dispatchers.Default) {
            repeat(3) { series ->
                launch {
                    scheduler.advanceTimeBy(100.milliseconds * (series + 1))
                    repeat(10) { number ->
                        collector.add(TimedResult(clock.now(), "$series.$number"))
                        scheduler.advanceTimeBy(2000.milliseconds)
                    }
                    scheduler.advanceUntilIdle()
                }
            }
        }.join()
        println(collector.joinToString("\n"))
        assertTrue { collector.sortedBy { it.time } == collector }
    }

    @Test
    fun contextAdvance() = runTest(timeout = 500.milliseconds) {
        VirtualTimeScheduler().runSimulation {
            val clock = asClock(Instant.fromEpochMilliseconds(0L))
            val collector = mutableListOf<TimedResult>()
            launch {
                repeat(3) { series ->
                    launch {
                        delay(100.milliseconds * (series + 1))
                        repeat((series + 1) * 10) { number ->
                            collector.add(TimedResult(clock.now(), "$series.$number"))
                            println(collector.last())
                            delay(2000.milliseconds)
                        }
                        //timeManager.pass(this)
                    }
                }
                launch {
                    delay(30.seconds)
                    collector.add(TimedResult(clock.now(), "side"))
                    println("Complete")
                }
            }.join()
//        println(collector.joinToString("\n"))
            assertTrue { collector.sortedBy { it.time } == collector }
        }
    }
}