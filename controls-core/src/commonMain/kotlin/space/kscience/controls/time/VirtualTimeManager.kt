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
import space.kscience.dataforge.context.Logger
import space.kscience.dataforge.context.debug
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
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

    private suspend fun handle(): VirtualTimeThread = coroutineContext[VirtualTimeThread]
        ?: VirtualTimeThread("scope[${coroutineContext.hashCode().toHexString()}]")

    /**
     * Read current time for the given [handle]. Handle time is always lower or equals to global manager time
     */
    public suspend fun readTime(): Instant = mutex.withLock { markerTimes[handle()] ?: time.value }

    /**
     * Set target of current scope timeline to [to] and wait for it to happen
     */
    public suspend fun advanceTimeTo(to: Instant) {
        val handle = handle()
        val currentMarkerTime = mutex.withLock { markerTimes[handle] ?: time.value }
        //if it is already the last instant - bypass
        if (currentMarkerTime == to) return
        // require that time is in the future
        require(to > currentMarkerTime) { "The target time for marker `$handle` $to is less that current marker time $currentMarkerTime" }

        // add auto-removal for new handlers
        if (handle !in markerTimes.keys) {
            coroutineContext[Job]?.invokeOnCompletion {
                markerTimes.remove(handle)
                advanceTime()
            }
        }

        handle.logTarget?.debug { "Advancing virtual time for handle ${handle.id} to $to from ${time.value}" }

        mutex.withLock {
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
    }

    /**
     * Mark given [handle] as idle so its time could advance to the time after all other handles. Then wait for the time to advance.
     */
    public suspend fun pass(handle: Any) {
        advanceTimeTo(markerTimes.values.max())
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

    override fun toString(): String = "VirtualTimeManager(time=${time.value}, markerTimes=$markerTimes)"
}

public suspend fun VirtualTimeManager.advanceTimeBy(duration: Duration) {
    advanceTimeTo(readTime() + duration)
}

/**
 * An identifier and debug options for coroutines associated with virtual time threads
 */
public class VirtualTimeThread(
    public val id: String,
    public val logTarget: Logger? = null
) : AbstractCoroutineContextElement(VirtualTimeThread) {

    public companion object : CoroutineContext.Key<VirtualTimeThread>
}

/**
 * A custom implementation of [CoroutineDispatcher] and [Delay] that interacts with a virtualized time mechanism.
 *
 * This dispatcher is designed to operate with a virtual clock provided by [VirtualTimeManager], allowing coroutines
 * to be scheduled and executed based on virtual time progression rather than real-world time.
 *
 * This class works in tandem with [VirtualTimeManager] to manage virtual time progression and coroutine scheduling,
 * enabling fine-grained control over time-based behavior in coroutine execution. It can be helpful in simulations,
 * testing, or any scenario where time needs to be manipulated or advanced deterministically.
 *
 * The dispatcher delegates its operations to an underlying dispatcher, either provided via [coroutineContext] or
 * defaulting to [Dispatchers.Default]. It overrides methods to handle coroutine scheduling, yielding, and time-based
 * resume operations, ensuring they operate based on virtual time.
 *
 * @constructor Creates an instance of [VirtualTimeDispatcher] with the specified [coroutineContext]
 * and [virtualTimeManager].
 * The constructor is internal and is not directly accessible for public instantiation.
 *
 * @param coroutineContext The coroutine context used by the dispatcher to delegate operations.
 * @param virtualTimeManager The virtual time manager that controls the virtual time progression.
 */
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
        val scheduledJob = scope.launch {
            withContext(continuation.context) {
                virtualTimeManager.advanceTimeBy(timeMillis.milliseconds)
            }
            dispatcher.dispatch(continuation.context) {
                @OptIn(ExperimentalCoroutinesApi::class)
                with(dispatcher) { with(continuation) { resumeUndispatched(Unit) } }
            }
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