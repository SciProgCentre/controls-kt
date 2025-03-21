package space.kscience.controls.time

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.kscience.controls.api.Device
import space.kscience.controls.instant
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToLong
import kotlin.time.Duration

@OptIn(InternalCoroutinesApi::class)
private class CompressedTimeDispatcher(
    val coroutineContext: CoroutineContext,
    val compression: Double,
) : CoroutineDispatcher(), Delay {

    val dispatcher = coroutineContext[CoroutineDispatcher] ?: Dispatchers.Default

    @InternalCoroutinesApi
    override fun dispatchYield(context: CoroutineContext, block: Runnable) {
        dispatcher.dispatchYield(context, block)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = dispatcher.isDispatchNeeded(context)

    override fun limitedParallelism(parallelism: Int, name: String?): CoroutineDispatcher =
        dispatcher.limitedParallelism(parallelism, name)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatcher.dispatch(context, block)
    }

    private val parentDelay = ((dispatcher as? Delay) ?: (Dispatchers.Default as Delay))

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        parentDelay.scheduleResumeAfterDelay((timeMillis / compression).roundToLong(), continuation)
    }


    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle =
        parentDelay.invokeOnTimeout((timeMillis / compression).roundToLong(), block, context)
}

private class CompressedClock(
    val baseClock: Clock = Clock.System,
    val compression: Double,
    val start: Instant = baseClock.now(),
) : Clock {
    override fun now(): Instant {
        val elapsed = (baseClock.now() - start)
        return start + elapsed / compression
    }
}

public sealed interface ClockMode {
    public data object System : ClockMode
    public data class Compressed(val compression: Double) : ClockMode
    public data class Virtual(val manager: VirtualTimeManager) : ClockMode
}

public class ClockManager : AbstractPlugin() {
    override val tag: PluginTag get() = Companion.tag

    public val clockMode: ClockMode = when (meta["clock.mode"].string) {
        null, "system" -> ClockMode.System
        "virtual" -> ClockMode.Virtual(VirtualTimeManager(meta["clock.start"]?.instant ?: Clock.System.now()))
        else -> ClockMode.Compressed(meta["clock.compression"].double ?: 1.0)
    }

    public val clock: Clock = when (clockMode) {
        is ClockMode.Compressed -> CompressedClock(Clock.System, clockMode.compression)
        ClockMode.System -> Clock.System
        is ClockMode.Virtual -> clockMode.manager
    }


    /**
     * Provide a [CoroutineDispatcher] with compressed time based on context dispatcher
     */
    public val dispatcher: CoroutineDispatcher = when (clockMode) {
        ClockMode.System -> context.coroutineContext[CoroutineDispatcher] ?: Dispatchers.Default
        is ClockMode.Compressed -> CompressedTimeDispatcher(context.coroutineContext, clockMode.compression)
        is ClockMode.Virtual -> VirtualTimeDispatcher(context.coroutineContext, clockMode.manager)
    }

    public fun scheduleWithFixedDelay(tick: Duration, block: suspend () -> Unit): Job = context.launch(dispatcher) {
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

public val Device.coroutineDispatcher: CoroutineDispatcher
    get() = context.plugins[ClockManager]?.dispatcher
        ?: context.coroutineContext[CoroutineDispatcher]
        ?: Dispatchers.Default

public fun ContextBuilder.withTimeCompression(compression: Double) {
    require(compression > 0.0) { "Time compression must be greater than zero." }
    plugin(ClockManager) {
        "timeCompression" put compression
    }
}