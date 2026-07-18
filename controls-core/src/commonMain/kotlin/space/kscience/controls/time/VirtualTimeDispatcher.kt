@file:OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
@file:Suppress("ERROR_SUPPRESSION")

package space.kscience.controls.time

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.ThreadSafeHeap
import kotlinx.coroutines.internal.ThreadSafeHeapNode
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.selects.SelectClause1
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * This class exists to allow cleanup code to avoid throwing for cancelled continuations scheduled
 * in the future.
 */
private class CancellableContinuationRunnable(
    val continuation: CancellableContinuation<Unit>,
    private val dispatcher: CoroutineDispatcher
) : Runnable {
    override fun run() = with(dispatcher) { with(continuation) { resumeUndispatched(Unit) } }
}


/**
 * Virtual time manager based on [kotlinx-coroutines-test](https://github.com/Kotlin/kotlinx.coroutines/tree/master/kotlinx-coroutines-test/common/src)
 * virtual time manager.
 *
 * This is a scheduler for coroutines used in tests, providing the delay-skipping behavior.
 *
 * [Test dispatchers][VirtualTimeDispatcher] are parameterized with a scheduler. Several dispatchers can share the
 * same scheduler, in which case their knowledge about the virtual time will be synchronized. When the dispatchers
 * require scheduling an event at a later point in time, they notify the scheduler, which will establish the order of
 * the tasks.
 *
 * The scheduler can be queried to advance the time (via [advanceTimeBy]), run all the scheduled tasks advancing the
 * virtual time as needed (via [advanceUntilIdle]), or run the tasks that are scheduled to run as soon as possible but
 * haven't yet been dispatched (via [runCurrent]).
 */
@OptIn(ExperimentalAtomicApi::class)
public class VirtualTimeDispatcher(
    parentScope: CoroutineScope
) : CoroutineDispatcher(), Delay, AutoCloseable {

    /** This heap stores the knowledge about which dispatchers are interested in which moments of virtual time. */
    // TODO: replace by ArrayDeque
    private val events = ThreadSafeHeap<VirtualTimeDispatchEvent<Any>>()

    /** Establishes that [currentTime] can't exceed the time of the earliest event in [events]. */
    private val lock = SynchronizedObject()

    /** This counter establishes some order on the events that happen at the same virtual time. */
    private val count = AtomicLong(0L)

    /** The current virtual time in milliseconds. */
    public var currentTime: Long = 0
        get() = synchronized(lock) { field }
        private set

    /** A channel for notifying about the fact that a foreground work dispatch recently happened. */
    private val dispatchEventsForeground: Channel<Unit> = Channel(CONFLATED)

    /** A channel for notifying about the fact that a dispatch recently happened. */
    private val dispatchEvents: Channel<Unit> = Channel(CONFLATED)

    /**
     * Registers a request for the scheduler at a virtual moment [timeDeltaMillis] milliseconds
     * later via [VirtualTimeDispatcher.processEvent], which will be called with the provided [marker] object.
     *
     * Returns the handler which can be used to cancel the registration.
     */
    private fun <T : Any> registerEvent(
        timeDeltaMillis: Long,
        marker: T,
        context: CoroutineContext,
    ): DisposableHandle {
        require(timeDeltaMillis >= 0) { "Attempted scheduling an event earlier in time (with the time delta $timeDeltaMillis)" }
//        checkSchedulerInContext(this, context)
        val count = count.fetchAndIncrement()
        val isForeground = context[BackgroundWork] === null
        return synchronized(lock) {
            val time = addClamping(currentTime, timeDeltaMillis)
            val event = VirtualTimeDispatchEvent(count, time, marker as Any, isForeground)
            events.addLast(event)
            /** can't be moved above: otherwise, [onDispatchEventForeground] or [onDispatchEvent] could consume the
             * token sent here before there's actually anything in the event queue. */
            sendDispatchEvent(context)
            DisposableHandle {
                synchronized(lock) {
                    events.remove(event)
                }
            }
        }
    }

    /**
     * Runs the next enqueued task, advancing the virtual time to the time of its scheduled awakening,
     * unless [condition] holds.
     */
    private fun tryRunNextTaskUnless(condition: () -> Boolean): Boolean {
        val event = synchronized(lock) {
            if (condition()) return false
            val event = events.removeFirstOrNull() ?: return false
            if (currentTime > event.time)
                currentTimeAheadOfEvents()
            currentTime = event.time
            event
        }
        processEvent(event.marker)
        return true
    }

    /**
     * Runs the enqueued tasks in the specified order, advancing the virtual time as needed until there are no more
     * tasks associated with the dispatchers linked to this scheduler.
     *
     * A breaking change from `TestCoroutineDispatcher.advanceTimeBy` is that it no longer returns the total number of
     * milliseconds by which the execution of this method has advanced the virtual time. If you want to recreate that
     * functionality, query [currentTime] before and after the execution to achieve the same result.
     */
    public fun advanceUntilIdle(): Unit = advanceUntilIdleOr { events.none(VirtualTimeDispatchEvent<*>::isForeground) }

    /**
     * [condition]: guaranteed to be invoked under the lock.
     */
    private fun advanceUntilIdleOr(condition: () -> Boolean) {
        while (true) {
            if (!tryRunNextTaskUnless(condition))
                return
        }
    }

    /**
     * Runs the tasks that are scheduled to execute at this moment of virtual time.
     */
    public fun runCurrent() {
        val timeMark = synchronized(lock) { currentTime }
        while (true) {
            val event = synchronized(lock) {
                events.removeFirstIf { it.time <= timeMark } ?: return
            }
            processEvent(event.marker)
        }
    }

    /**
     * Moves the virtual clock of this dispatcher forward by [the specified amount][delayTime], running the
     * scheduled tasks in the meantime.
     *
     * @throws IllegalArgumentException if passed a negative [delay][delayTime].
     */
    public fun advanceTimeBy(delayTime: Duration) {
        require(!delayTime.isNegative()) { "Can not advance time by a negative delay: $delayTime" }
        val startingTime = currentTime
        val targetTime = addClamping(startingTime, delayTime.inWholeMilliseconds)
        while (true) {
            val event = synchronized(lock) {
                val timeMark = currentTime
                val event = events.removeFirstIf { targetTime > it.time }
                when {
                    event == null -> {
                        currentTime = targetTime
                        return
                    }

                    timeMark > event.time -> currentTimeAheadOfEvents()
                    else -> {
                        currentTime = event.time
                        event
                    }
                }
            }
            processEvent(event.marker)
        }
    }

    /**
     * Notifies this scheduler about a dispatch event.
     *
     * [context] is the context in which the task will be dispatched.
     */
    private fun sendDispatchEvent(context: CoroutineContext) {
        dispatchEvents.trySend(Unit)
        if (context[BackgroundWork] !== BackgroundWork)
            dispatchEventsForeground.trySend(Unit)
    }

    /**
     * Waits for a notification about a dispatch event.
     */
    private suspend fun receiveDispatchEvent() = dispatchEvents.receive()

    /**
     * Consumes the knowledge that a dispatch event happened recently.
     */
    private val onDispatchEvent: SelectClause1<Unit> get() = dispatchEvents.onReceive

    /**
     * Consumes the knowledge that a foreground work dispatch event happened recently.
     */
    private val onDispatchEventForeground: SelectClause1<Unit> get() = dispatchEventsForeground.onReceive

    /**
     * Returns the [TimeSource] representation of the virtual time of this scheduler.
     */
    public val timeSource: TimeSource.WithComparableMarks = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
        override fun read(): Long = currentTime
    }

    /** Notifies the dispatcher that it should process a single event marked with [marker] happening at time [time]. */
    private fun processEvent(marker: Any) {
        check(marker is Runnable)
        marker.run()
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val timedRunnable = CancellableContinuationRunnable(continuation, this)
        val handle = registerEvent(
            timeMillis,
            timedRunnable,
            continuation.context,
        )
        continuation.disposeOnCancellation(handle)
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle =
        registerEvent(timeMillis, block, context)


    override fun dispatch(context: CoroutineContext, block: Runnable) {
        registerEvent(0, block, context)
    }

    //TODO add pause to eventLoop
    private val eventLoopJob: Job = parentScope.launch(CoroutineName("Controls virtual time runner")) {
        while (true) {
            val executedSomething = tryRunNextTaskUnless { !isActive }
            if (executedSomething) {
                /** yield to check for cancellation. On JS, we can't use [ensureActive] here, as the cancellation
                 * procedure needs a chance to run concurrently. */
                yield()
            } else {
                // waiting for the next task to be scheduled, or for the test runner to be cancelled
                receiveDispatchEvent()
            }
        }
    }

    override fun close() {
        eventLoopJob.cancel()
    }
}

/**
 * Create a [Clock] based on this scheduler with given time offset for simulation start
 */
public fun VirtualTimeDispatcher.asClock(startTime: Instant = Clock.System.now()): Clock = object : Clock {
    override fun now(): Instant = startTime + currentTime.milliseconds
}

public class VirtualTimeScope internal constructor(
    override val coroutineContext: CoroutineContext,
    private val dispatcher: VirtualTimeDispatcher,
    timeOffset: Instant = Clock.System.now(),
) : CoroutineScope {

    public val clock: Clock = dispatcher.asClock(timeOffset)

}

/**
 * Executes a suspending block of code within a virtual time scope. This allows you to control and manipulate
 * the passage of time for simulation purposes using a virtual time dispatcher.
 *
 * @param timeOffset The initial time offset to start the virtual time clock from. Defaults to the current system time.
 * @param block The suspending block of code to be executed within the virtual time scope.
 * @return Nothing. The function completes normally after executing the given block within the virtual time scope.
 */
public suspend fun virtualTimeScope(
    timeOffset: Instant = Clock.System.now(),
    block: suspend VirtualTimeScope.() -> Unit
): Unit = coroutineScope {
    val currentDispatcher = coroutineContext[ContinuationInterceptor.Key] as? CoroutineDispatcher as? VirtualTimeDispatcher
    //if already on virtual time, just launch the block
    if (currentDispatcher != null) {
        val scope = VirtualTimeScope(coroutineContext, currentDispatcher, timeOffset = timeOffset)
        scope.block()
    } else {
        //if it is not, create a dispatcher and close it after use
        val dispatcher: VirtualTimeDispatcher = VirtualTimeDispatcher(this)
        dispatcher.use {
            withContext(dispatcher) {
                val scope = VirtualTimeScope(coroutineContext, dispatcher, timeOffset = timeOffset)
                scope.block()
            }
        }
    }
}

// Some error-throwing functions for pretty stack traces
private fun currentTimeAheadOfEvents(): Nothing = invalidSchedulerState()

private fun invalidSchedulerState(): Nothing =
    throw IllegalStateException("The test scheduler entered an invalid state. Please report this at https://github.com/Kotlin/kotlinx.coroutines/issues.")

/** [ThreadSafeHeap] node representing a scheduled task, ordered by the planned execution time. */
private class VirtualTimeDispatchEvent<T>(
    private val count: Long,
    val time: Long,
    val marker: T,
    val isForeground: Boolean,
) : Comparable<VirtualTimeDispatchEvent<*>>, ThreadSafeHeapNode {
    override var heap: ThreadSafeHeap<*>? = null
    override var index: Int = 0

    override fun compareTo(other: VirtualTimeDispatchEvent<*>) =
        compareValuesBy(this, other, VirtualTimeDispatchEvent<*>::time, VirtualTimeDispatchEvent<*>::count)

    override fun toString() = "VirtualTimeDispatchEvent(time=$time)"
}

// works with positive `a`, `b`
private fun addClamping(a: Long, b: Long): Long = (a + b).let { if (it >= 0) it else Long.MAX_VALUE }

internal object BackgroundWork : CoroutineContext.Key<BackgroundWork>, CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = this

    override fun toString(): String = "BackgroundWork"
}

private fun <T> ThreadSafeHeap<T>.none(
    predicate: (T) -> Boolean
) where T : ThreadSafeHeapNode, T : Comparable<T> = find(predicate) == null