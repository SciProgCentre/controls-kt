package space.kscience.simulation

import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TimelineTests {


    @Test
    fun testGeneration() = runTest(timeout = 1.seconds) {
        val startTime = Instant.parse("2020-01-01T00:00:00.000Z")

        val generation = GeneratingTimeline(
            origin = TimelineEvent(startTime, Unit),
            lookaheadInterval = 1.seconds,
            timeOf = WithTime::time
        ) { event ->
            var time = event.time
            while (isActive) {
                time += 0.1.seconds
                println("Emit: ${time - startTime}")
                emit(TimelineEvent(time, Unit))
            }
        }

        val result = mutableListOf<Duration>()

        val observer = generation.observeEach {
            println("Consume: ${it.time - startTime}")
            result.add(it.time - startTime)
        }

        observer.collect(2.seconds)
        println("First collection complete")
        observer.collect(2.seconds)
        println("Second collection complete")
        println("Interrupt")
//        generation.interrupt(TimelineEvent(startTime + 6.seconds, Unit))
//        println("Collecting after interruption")
        observer.collect(startTime + 6.seconds + 2.5.seconds)
        println(result)
        generation.close()

    }
}