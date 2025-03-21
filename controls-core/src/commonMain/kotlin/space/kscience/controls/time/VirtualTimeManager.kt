package space.kscience.controls.time

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

public class VirtualTimeManager(
    startTime: Instant,
) : Clock {
    private val _time = MutableStateFlow(startTime)
    public val time: StateFlow<Instant> get() = _time

    override fun now(): Instant = _time.value

    private val markerTimes = mutableMapOf<Any, Instant>()

    /**
     * Set target of [handle] timeline to [to] and wait for it to happen
     */
    public suspend fun advanceTime(handle: Any, to: Instant) {
        val currentMarkerTime = markerTimes[handle] ?: now()
        require(to > currentMarkerTime) { "The advanced time for marker `$handle` $to is less that current marker time $currentMarkerTime" }
        markerTimes[handle] = to
        // advance time if necessary
        _time.emit(markerTimes.values.min())
        // wait for time to exceed marker time
        time.first { it >= to }
    }

}

@OptIn(InternalCoroutinesApi::class)
public class VirtualTimeDispatcher internal constructor(
    private val coroutineContext: CoroutineContext,
    private val virtualTimeManager: VirtualTimeManager
) : CoroutineDispatcher(), Delay {

    private val scope = CoroutineScope(coroutineContext)

    public val dispatcher: CoroutineDispatcher =
        coroutineContext[CoroutineDispatcher] ?: Dispatchers.Default

    override fun dispatch(context: CoroutineContext, block: Runnable): Unit = dispatcher.dispatch(context, block)

    override fun limitedParallelism(
        parallelism: Int,
        name: String?
    ): CoroutineDispatcher = VirtualTimeDispatcher(
        coroutineContext = coroutineContext + dispatcher.limitedParallelism(parallelism, name),
        virtualTimeManager = virtualTimeManager
    )

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = dispatcher.isDispatchNeeded(context)

    @InternalCoroutinesApi
    override fun dispatchYield(context: CoroutineContext, block: Runnable) {
        dispatcher.dispatchYield(context, block)
    }

    override fun toString(): String = dispatcher.toString()

    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? = dispatcher[key]

    override fun scheduleResumeAfterDelay(
        timeMillis: Long,
        continuation: CancellableContinuation<Unit>
    ) {
        val handle = continuation.context[Job] ?: error("Can't use VirtualTimeDispatcher without Job")

        val scheduledJob = scope.launch {
            virtualTimeManager.advanceTime(handle, virtualTimeManager.time.value + timeMillis.milliseconds)
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