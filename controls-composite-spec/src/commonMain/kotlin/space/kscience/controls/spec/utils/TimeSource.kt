package space.kscience.controls.spec.utils

import kotlinx.coroutines.delay as kotlinDelay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Plugin
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import kotlin.time.Duration

/**
 * Abstraction for working with time and delays.
 * Allows injection of custom time sources for testing.
 */
public interface TimeSource : Plugin {
    public fun now(): Instant
    public suspend fun delay(duration: Duration)
    override val tag: PluginTag get() = Factory.tag

    public companion object Factory : PluginFactory<TimeSource> {
        override val tag: PluginTag = PluginTag("TimeSource", PluginTag.DATAFORGE_GROUP)
        override fun build(context: Context, meta: Meta): TimeSource = SystemTimeSource
    }
}

/**
 * Standard implementation of [TimeSource] using system clock and coroutine delay.
 */
public object SystemTimeSource : TimeSource {
    override fun now(): Instant = Clock.System.now()
    override suspend fun delay(duration: Duration) { kotlinDelay(duration) }
    override val tag: PluginTag = PluginTag("SystemTimeSource", PluginTag.DATAFORGE_GROUP)
    override val meta: Meta get() = Meta.EMPTY
    override val context: Context get() = throw UnsupportedOperationException("SystemTimeSource is a global object and not bound to a specific context.")
    override val isAttached: Boolean = false
    override fun dependsOn(): Map<PluginFactory<*>, Meta> = emptyMap()
    override fun attach(context: Context) { /* No-op */ }
    override fun detach() { /* No-op */ }
}

/**
 * Extension to get [TimeSource] from context.
 */
public fun Context.getTimeSource(default: TimeSource = SystemTimeSource): TimeSource =
    plugins[TimeSource.Factory.tag] as? TimeSource ?: default

/**
 * Convenience property to get [TimeSource] from context, or [SystemTimeSource] if none.
 */
public val Context.timeSourceOrDefault: TimeSource get() = getTimeSource()