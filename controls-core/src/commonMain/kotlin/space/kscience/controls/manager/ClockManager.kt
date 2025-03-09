package space.kscience.controls.manager

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.kscience.controls.api.Device
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToLong
import kotlin.time.Duration

@OptIn(InternalCoroutinesApi::class)
private class CompressedTimeDispatcher(
    val clockManager: ClockManager,
    val dispatcher: CoroutineDispatcher,
    val compression: Double,
) : CoroutineDispatcher(), Delay {

    @InternalCoroutinesApi
    override fun dispatchYield(context: CoroutineContext, block: Runnable) {
        dispatcher.dispatchYield(context, block)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = dispatcher.isDispatchNeeded(context)

    @ExperimentalCoroutinesApi
    override fun limitedParallelism(parallelism: Int): CoroutineDispatcher = dispatcher.limitedParallelism(parallelism)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatcher.dispatch(context, block)
    }

    private val delay = ((dispatcher as? Delay) ?: (Dispatchers.Default as Delay))

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        delay.scheduleResumeAfterDelay((timeMillis / compression).roundToLong(), continuation)
    }


    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        return delay.invokeOnTimeout((timeMillis / compression).roundToLong(), block, context)
    }
}

private class CompressedClock(
    val start: Instant,
    val compression: Double,
    val baseClock: Clock = Clock.System,
) : Clock {
    override fun now(): Instant {
        val elapsed = (baseClock.now() - start)
        return start + elapsed / compression
    }
}

public class ClockManager : AbstractPlugin() {
    override val tag: PluginTag get() = Companion.tag

    public val timeCompression: Double by meta.double(1.0)

    public val clock: Clock by lazy {
        if (timeCompression == 1.0) {
            Clock.System
        } else {
            CompressedClock(Clock.System.now(), timeCompression)
        }
    }

    /**
     * Provide a [CoroutineDispatcher] with compressed time based on given [dispatcher]
     */
    public fun asDispatcher(
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): CoroutineDispatcher = if (timeCompression == 1.0) {
        dispatcher
    } else {
        CompressedTimeDispatcher(this, dispatcher, timeCompression)
    }

    public fun scheduleWithFixedDelay(tick: Duration, block: suspend () -> Unit): Job = context.launch(asDispatcher()) {
        while (isActive) {
            delay(tick)
            block()
        }
    }


    public companion object : PluginFactory<ClockManager> {
        override val tag: PluginTag = PluginTag("clock", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): ClockManager = ClockManager()
    }
}

public val Context.clock: Clock get() = plugins[ClockManager]?.clock ?: Clock.System

public val Device.clock: Clock get() = context.clock

public fun Device.getCoroutineDispatcher(dispatcher: CoroutineDispatcher = Dispatchers.Default): CoroutineDispatcher =
    context.plugins[ClockManager]?.asDispatcher(dispatcher) ?: dispatcher

public fun ContextBuilder.withTimeCompression(compression: Double) {
    require(compression > 0.0) { "Time compression must be greater than zero." }
    plugin(ClockManager) {
        "timeCompression" put compression
    }
}