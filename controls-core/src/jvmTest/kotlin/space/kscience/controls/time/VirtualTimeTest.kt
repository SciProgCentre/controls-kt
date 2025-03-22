package space.kscience.controls.time

import kotlinx.coroutines.*
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

private data class TimedResult(val time: Instant, val marker: String)

class VirtualTimeTest {
    @Test
    fun manualAdvance(): Unit {
        val timeManager = VirtualTimeManager(Instant.fromEpochMilliseconds(0L))
        val collector = mutableListOf<TimedResult>()
        runBlocking(Dispatchers.Default) {
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
    fun contextAdvance(): Unit {
        val timeManager = VirtualTimeManager(Instant.fromEpochMilliseconds(0L))
        val collector = mutableListOf<TimedResult>()
        runBlocking(Dispatchers.Default.withVirtualTime(timeManager)) {
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
            }
        }
        println(collector.joinToString("\n"))
        assertTrue { collector.sortedBy { it.time } == collector }
    }
}