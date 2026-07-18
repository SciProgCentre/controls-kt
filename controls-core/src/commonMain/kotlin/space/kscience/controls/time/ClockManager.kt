package space.kscience.controls.time

import kotlinx.coroutines.*
import space.kscience.controls.instant
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.*
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@OptIn(InternalCoroutinesApi::class)
private class CompressedTimeDispatcher(
    val coroutineContext: CoroutineContext,
    val compression: Double,
) : CoroutineDispatcher(), Delay {

    val dispatcher = coroutineContext[ContinuationInterceptor.Key] as? CoroutineDispatcher ?: Dispatchers.Default

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

/**
 * Represents the mode of operation for a clock, defining its time source or behavior.
 */
public sealed interface ClockMode {
    public data object System : ClockMode
    public data class Custom(public val clock: Clock) : ClockMode
    public data class Compressed(public val compression: Double) : ClockMode
    public data class Virtual(public val scheduler: VirtualTimeDispatcher) : ClockMode
}

/**
 * Manages clock-related functionality and provides different modes of clock operation
 * based on the provided metadata. This includes system clocks, virtual clocks,
 * compressed time clocks, and custom-defined clocks.
 *
 * @constructor Initializes the `ClockManager` with the given metadata.
 *
 * @property clockMode Specifies the operational mode of the clock. The mode is resolved
 * based on the metadata, and it can be one of the following: system clock, virtual clock,
 * compressed time clock, or a custom clock.
 *
 * @property clock Provides the actual clock instance based on the resolved `clockMode`.
 * Supports a variety of clock implementations, such as system, virtual, compressed, or
 * custom clocks based on the configuration.
 *
 * @property simulationDispatcher Provides a `CoroutineDispatcher` to manage coroutine
 * execution for simulations using the same time management methodology as dictated by
 * the `clockMode`.
 *
 * @function scheduleWithFixedDelay Launches a coroutine that executes a given block
 * repeatedly with a fixed delay between executions. The delay is compatible with the
 * managed time provided by the clock in `simulationDispatcher`.
 */
public class ClockManager(meta: Meta) : AbstractPlugin(meta) {
    override val tag: PluginTag get() = Companion.tag


    public val clockMode: ClockMode by lazy {
        when (meta["clock.mode"].string) {
            null, "system" -> ClockMode.System
            "virtual" -> ClockMode.Virtual(VirtualTimeDispatcher(context))
            "compressed" -> ClockMode.Compressed(meta["clock.compression"].double ?: 1.0)
            else -> error("Can't resolve clock for $meta")
        }
    }

    public val clock: Clock by lazy {
        when (val mode = clockMode) {
            ClockMode.System -> Clock.System
            is ClockMode.Custom -> mode.clock
            is ClockMode.Compressed -> CompressedClock(Clock.System, mode.compression)
            is ClockMode.Virtual -> mode.scheduler.asClock(meta["clock.start"]?.instant ?: Clock.System.now())
        }
    }

    /**
     * Provide a [CoroutineDispatcher] with time management for simulations
     */
    public val simulationDispatcher: CoroutineDispatcher by lazy {
        when (val mode = clockMode) {
            is ClockMode.System, is ClockMode.Custom ->
                context.coroutineContext[ContinuationInterceptor.Key] as? CoroutineDispatcher ?: Dispatchers.Default

            is ClockMode.Compressed -> CompressedTimeDispatcher(
                coroutineContext = context.coroutineContext,
                compression = mode.compression
            )

            is ClockMode.Virtual -> mode.scheduler
        }
    }

    public fun scheduleWithFixedDelay(tick: Duration, block: suspend () -> Unit): Job =
        context.launch(simulationDispatcher) {
            while (isActive) {
                delay(tick)
                block()
            }
        }

    override fun detach() {
        (clockMode as? ClockMode.Virtual)?.scheduler?.close()
        super.detach()
    }

    public companion object : PluginFactory<ClockManager> {
        override val tag: PluginTag = PluginTag("clock", group = PluginTag.DATAFORGE_GROUP)

        /**
         * The default instance of [ClockManager].
         *
         * This instance is configured with an empty meta configuration ([Meta.EMPTY]) and is
         * associated with the global clock context ([Global]).
         * It serves as the default clock management utility within the system and provides
         * functionality for scheduling and managing tasks related to time.
         */
        public val DEFAULT: ClockManager = ClockManager(Meta.EMPTY).apply { attach(Global) }

        override fun build(
            context: Context,
            meta: Meta
        ): ClockManager = ClockManager(Laminate(meta, context.properties))
    }
}

public val Context.clockManager: ClockManager get() = plugins[ClockManager] ?: ClockManager.DEFAULT

public val Context.clock: Clock get() = plugins[ClockManager]?.clock ?: Clock.System

/**
 * A special device dispatcher that takes into account context time management options
 */
public val Context.deviceDispatcher: CoroutineDispatcher
    get() = plugins[ClockManager]?.simulationDispatcher
        ?: coroutineContext[ContinuationInterceptor.Key] as? CoroutineDispatcher
        ?: Dispatchers.Default

public fun ContextBuilder.withTimeCompression(compression: Double) {
    require(compression > 0.0) { "Time compression must be greater than zero." }
    plugin(ClockManager) {
        "clock" put {
            "mode" put "compressed"
            "compression" to compression
        }
    }
}

public fun ContextBuilder.withVirtualTime(start: Instant = Clock.System.now()) {
    plugin(ClockManager) {
        "clock" put {
            "mode" put "virtual"
            "start" put start.toString()
        }
    }
}