@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package space.kscience.controls.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import space.kscience.dataforge.context.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ClockCompressionTest {
    @Test
    fun configuredCompressionIsUsed() {
        val context = Context { withTimeCompression(10.0) }
        try {
            assertEquals(ClockMode.Compressed(10.0), context.clockManager.clockMode)
        } finally {
            context.close()
        }
    }

    @Test
    fun compressedClockScalesElapsedTime() {
        val start = Instant.fromEpochSeconds(1_000)
        var now = start
        val base = object : Clock {
            override fun now(): Instant = now
        }
        val clock = CompressedClock(base, 10.0)
        now += 2.seconds
        assertEquals(start + 20.seconds, clock.now())
        now -= 1.seconds
        assertEquals(start + 10.seconds, clock.now())
    }

    @Test
    fun compressedDelayUsesConfiguredFactor() = runTest {
        val context = Context {
            coroutineContext(StandardTestDispatcher(testScheduler))
            withTimeCompression(10.0)
        }
        try {
            withContext(context.clockManager.simulationDispatcher) {
                delay(1.seconds)
            }
            assertEquals(100L, currentTime)
        } finally {
            context.close()
        }
    }
}
