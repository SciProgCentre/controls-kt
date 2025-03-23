package space.kscience.controls.time

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

public class VirtualTimeManager(
    startTime: Instant,
) : Clock {
    private val _time = MutableStateFlow(startTime)

    /**
     * Advance time taking into account remaining branches
     */
    private fun advanceTime() {
        markerTimes.values.minOrNull()?.let {
            _time.value = it
        }
    }

    public val time: StateFlow<Instant> get() = _time

    override fun now(): Instant = _time.value

    private val markerTimes = mutableMapOf<Any, Instant>()

    private val mutex = Mutex()

    /**
     * Read current time for the given [handle]. Handle time is always lower or equals to global manager time
     */
    public suspend fun readTime(handle: Any): Instant = mutex.withLock { markerTimes[handle] ?: time.value }

    /**
     * Set target of [handle] timeline to [to] and wait for it to happen
     */
    public suspend fun advanceTimeTo(handle: Any, to: Instant) {
        mutex.withLock {
            val currentMarkerTime = readTime(handle)
            //if it is already the last instant - bypass
            if (currentMarkerTime == to) return
            // require that time is in the future
            require(to > currentMarkerTime) { "The target time for marker `$handle` $to is less that current marker time $currentMarkerTime" }

//        println("$handle locked at $currentMarkerTime")

            if (handle is Job && handle !in markerTimes.keys) {
                //clear job marker on completion
                handle.invokeOnCompletion {
                    markerTimes.remove(handle)
                    advanceTime()
                }
            }
            markerTimes[handle] = to
            // advance time if necessary
            advanceTime()
        }

        // wait for time to exceed marker time
        if (time.value < to) {
            time.takeWhile {
                it < to
            }.collect()
        }
//        println("$handle unlocked at $currentMarkerTime")
    }

    /**
     * Mark given [handle] as idle so its time could advance to the time after all other handles. Then wait for the time to advance.
     */
    public suspend fun pass(handle: Any) {
        advanceTimeTo(handle, markerTimes.values.max())
        mutex.withLock {
            markerTimes.remove(handle)
        }
    }

    /**
     * Mark the whole manager as idle and advance time to the maximum of all handles. Don't wait for time to advance
     */
    public suspend fun pass() {
        _time.value = markerTimes.values.max()
        mutex.withLock {
            markerTimes.clear()
        }
    }
}

public suspend fun VirtualTimeManager.advanceTimeBy(handle: Any, duration: Duration) {
    advanceTimeTo(handle, readTime(handle) + duration)
}

@OptIn(InternalCoroutinesApi::class)
public class VirtualTimeDispatcher internal constructor(
    private val coroutineContext: CoroutineContext,
    private val virtualTimeManager: VirtualTimeManager
) : CoroutineDispatcher(), Delay {

    private val scope = CoroutineScope(coroutineContext)

    private val dispatcher: CoroutineDispatcher = coroutineContext[CoroutineDispatcher] ?: Dispatchers.Default

    override fun dispatch(context: CoroutineContext, block: Runnable): Unit = dispatcher.dispatch(context, block)

    override fun limitedParallelism(
        parallelism: Int,
        name: String?
    ): CoroutineDispatcher = VirtualTimeDispatcher(
        coroutineContext = dispatcher.limitedParallelism(parallelism, name),
        virtualTimeManager = virtualTimeManager
    )

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = dispatcher.isDispatchNeeded(context)

    @InternalCoroutinesApi
    override fun dispatchYield(context: CoroutineContext, block: Runnable) {
        dispatcher.dispatchYield(context, block)
    }

    override fun toString(): String = "VirtualTimeDispatcher($virtualTimeManager)"

    override fun scheduleResumeAfterDelay(
        timeMillis: Long,
        continuation: CancellableContinuation<Unit>
    ) {
        val handle = continuation.context[Job] ?: error("Can't use VirtualTimeDispatcher without Job")

        val scheduledJob = scope.launch {
            virtualTimeManager.advanceTimeBy(handle, timeMillis.milliseconds)
            dispatcher.dispatch(
                continuation.context,
                Runnable {
                    @OptIn(ExperimentalCoroutinesApi::class)
                    with(dispatcher) { with(continuation) { resumeUndispatched(Unit) } }
                }
            )
        }

        continuation.disposeOnCancellation {
            scheduledJob.cancel()
        }

    }
}

public fun CoroutineContext.withVirtualTime(
    virtualTimeManager: VirtualTimeManager
): CoroutineContext = if (this[Job] != null) {
    this
} else {
    //add job if it is not present
    plus(Job(null))
}.plus(VirtualTimeDispatcher(this, virtualTimeManager))