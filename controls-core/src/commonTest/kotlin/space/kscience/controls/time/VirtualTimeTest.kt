@file:OptIn(ExperimentalCoroutinesApi::class)

package space.kscience.controls.time

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import space.kscience.dataforge.context.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private data class TimedResult(val time: Instant, val marker: String)

class VirtualTimeTest {
    @Test
    fun dispatcherAdvance() = runTest(timeout = 500.milliseconds) {
        val collector = mutableListOf<TimedResult>()
        virtualTimeScope {
            repeat(3) { series ->
                launch {
                    delay(100.milliseconds * (series + 1))
                    repeat(10) { number ->
                        collector.add(TimedResult(clock.now(), "$series.$number"))
                        delay(2000.milliseconds)
                    }
                }
            }
        }
        println(collector.joinToString("\n"))
        assertTrue { collector.distinctBy { it.time }.size > 4 }
        assertEquals(collector, collector.sortedBy { it.time })
    }

    @Test
    fun testTestAdvance() = runTest(timeout = 200.milliseconds) {

        delay(1000)
        assertEquals(currentTime, 1000)

        launch {
            delay(500)
            assertEquals(currentTime, 1500)
        }

        launch {
            delay(1000)
            assertEquals(currentTime, 2000)
        }.join()

    }

    @Test
    fun contextAdvance() = runTest(timeout = 500.milliseconds) {
        val context = Context {
            withVirtualTime(Instant.fromEpochMilliseconds(0L))
        }
        
        val clockManager = context.plugins[ClockManager]!!
        val clock = clockManager.clock

        withContext(clockManager.simulationDispatcher) {
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
                    }
                }
                launch {
                    delay(30.seconds)
                    collector.add(TimedResult(clock.now(), "side"))
                    println("Complete")
                }
            }.join()
//        println(collector.joinToString("\n"))
            assertTrue { collector.distinctBy { it.time }.size > 4 }
            assertTrue { collector.sortedBy { it.time } == collector }
        }
    }
}