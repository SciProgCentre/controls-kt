package space.kscience.simulation

import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TimelineTests {


    @Test
    fun testGeneration() = runTest{
        val startTime = Instant.parse("2020-01-01T00:00:00.000Z")

        val generation = GeneratingTimeline<SimpleTimelineEvent<DoubleArray>>(
            this,
            origin = SimpleTimelineEvent(startTime, List(10) { it.toDouble() }.toDoubleArray()),
            lookaheadInterval = 1.seconds
        ) { event ->
            var time = event.time
            while (isActive) {
                time += 0.1.seconds
                println("Emit: ${time - startTime}")
                emit(SimpleTimelineEvent(time, event.value.map { it + 1.0 }.toDoubleArray()))
            }
        }

        val result = mutableListOf<Duration>()

        val collector = generation.observe {
            collect {
                println("Consume: ${it.time - startTime}")
                result.add(it.time - startTime)
            }
        }

        collector.collect(2.seconds)
        println("First collection complete")
        collector.collect(2.seconds)
        println("Second collection complete")
        println("Interrupt")
        generation.interrupt(SimpleTimelineEvent(startTime + 6.seconds, List(10) { it.toDouble() }.toDoubleArray()))
        println("Collecting second")
        collector.collect(startTime + 6.seconds + 2.5.seconds)
        println(result)
        collector.close()
    }
}