package space.kscience.simulation

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class TimelineTests {


    @Test
    fun testGeneration() = runTest(timeout = 5.seconds) {
        val startTime = Instant.parse("2020-01-01T00:00:00.000Z")

        val generation = GeneratingTimeline<SimpleTimelineEvent<DoubleArray>>(
            this,
            initialEvent = SimpleTimelineEvent(startTime, List(10) { it.toDouble() }.toDoubleArray()),
            lookaheadInterval = 1.seconds
        ) { event ->
            val time = event.time + 0.1.seconds
            println("Emit: $time")
            SimpleTimelineEvent(time, event.value.map { it + 1.0 }.toDoubleArray())
        }

        val collector = generation.observe {
            collect {
                println("Consume: ${it.time}")
            }
        }

        collector.collect(startTime + 2.seconds)
        collector.close()
    }
}