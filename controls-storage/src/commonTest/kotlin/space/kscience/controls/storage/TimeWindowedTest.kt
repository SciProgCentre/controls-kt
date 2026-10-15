@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package space.kscience.controls.storage

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TimeWindowedTest {
    @Test
    fun completionWhileTimerSendIsBlockedPreservesBatch() = runTest {
        val batches = withTimeout(1.seconds) {
            flow {
                emit(1)
                delay(20)
                emit(2)
                delay(20)
            }.timeWindowed(10.milliseconds).buffer(0)
                .onEach { delay(100) }.toList()
        }
        assertEquals(listOf(listOf(1), listOf(2)), batches)
    }

    @Test
    fun elementArrivingWhilePreviousSendIsBlockedGetsItsOwnWindow() = runTest {
        val batches = withTimeout(1.seconds) {
            flow {
                emit(1)
                delay(20)
                emit(2)
                delay(20)
                emit(3)
                awaitCancellation()
            }.timeWindowed(10.milliseconds).buffer(0).take(3)
                .onEach { delay(100) }.toList()
        }
        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), batches)
    }

    @Test
    fun windowStartsWithFirstElementAndFlushesWhileSourceIsActive() = runTest {
        val batches = mutableListOf<List<Int>>()
        var stopped = false
        val job = backgroundScope.launch {
            flow {
                try {
                    delay(100)
                    emit(1)
                    delay(60)
                    emit(2)
                    awaitCancellation()
                } finally {
                    stopped = true
                }
            }.timeWindowed(100.milliseconds).toList(batches)
        }
        runCurrent()
        advanceTimeBy(199)
        runCurrent()
        assertEquals(emptyList(), batches)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(listOf(1, 2)), batches)
        job.cancelAndJoin()
        assertTrue(stopped)
    }

    @Test
    fun finiteSourceFlushesTailAndCompletes() = runTest {
        val batches = withTimeout(1.seconds) {
            flow {
                emit(1)
                delay(20)
                emit(2)
                delay(150)
                emit(3)
            }.timeWindowed(100.milliseconds).toList()
        }
        assertEquals(listOf(listOf(1, 2), listOf(3)), batches)
    }

    @Test
    fun emptySourceCompletesWithoutBatches() = runTest {
        assertEquals(emptyList(), withTimeout(1.seconds) {
            emptyFlow<Int>().timeWindowed(100.milliseconds).toList()
        })
    }

    @Test
    fun nullableElementsAreNotTreatedAsCompletion() = runTest {
        assertEquals(listOf(listOf(null, 1)), withTimeout(1.seconds) {
            flowOf(null, 1).timeWindowed(100.milliseconds).toList()
        })
    }

    @Test
    fun completionAtWindowBoundaryDoesNotDuplicateOrLoseElements() = runTest {
        val batches = withTimeout(1.seconds) {
            flow {
                emit(1)
                delay(100)
                emit(2)
            }.timeWindowed(100.milliseconds).toList()
        }
        assertEquals(listOf(1, 2), batches.flatten())
        assertTrue(batches.all { it.isNotEmpty() })
    }

    @Test
    fun slowCollectorPreservesOrderAndCompletes() = runTest {
        val batches = mutableListOf<List<Int>>()
        withTimeout(5.seconds) {
            flow {
                repeat(30) {
                    emit(it)
                    delay(10)
                }
            }.timeWindowed(50.milliseconds).collect {
                batches.add(it)
                delay(120)
            }
        }
        assertEquals((0 until 30).toList(), batches.flatten())
    }

    @Test
    fun sourceFailureDoesNotFlushPendingElements() = runTest {
        val batches = mutableListOf<List<Int>>()
        assertFailsWith<IllegalStateException> {
            flow {
                emit(1)
                throw IllegalStateException("Source failed")
            }.timeWindowed(100.milliseconds).toList(batches)
        }
        assertEquals(emptyList(), batches)
    }

    @Test
    fun cancellationDoesNotFlushPendingElements() = runTest {
        val batches = mutableListOf<List<Int>>()
        var stopped = false
        val job = backgroundScope.launch {
            flow {
                try {
                    emit(1)
                    awaitCancellation()
                } finally {
                    stopped = true
                }
            }.timeWindowed(100.milliseconds).toList(batches)
        }
        runCurrent()
        advanceTimeBy(50)
        job.cancelAndJoin()
        assertEquals(emptyList(), batches)
        assertTrue(stopped)
    }

    @Test
    fun infiniteWindowFlushesOnNormalCompletion() = runTest {
        assertEquals(listOf(listOf(1, 2)), withTimeout(1.seconds) {
            flowOf(1, 2).timeWindowed(Duration.INFINITE).toList()
        })
    }

    @Test
    fun windowDurationMustBePositive() {
        for (duration in listOf(Duration.ZERO, -1.milliseconds)) {
            assertFailsWith<IllegalArgumentException> {
                emptyFlow<Int>().timeWindowed(duration)
            }
        }
    }
}
