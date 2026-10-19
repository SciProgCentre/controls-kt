package space.kscience.controls.tagtable

import kotlinx.coroutines.flow.StateFlow
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.WithLifeCycle
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaRef
import space.kscience.dataforge.meta.MetaSpec
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.meta.copy
import space.kscience.dataforge.meta.enum
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import kotlin.time.Duration

/**
 * Represents the state of a tag source, encapsulating metadata and relevant attributes.
 */
@JvmInline
public value class SourceState(public val value: Meta) {
    public val sourceTimeOffset: Duration? get() = value[SourceState.sourceTimeOffset]?.let { Duration.parse(it) }
    public val lifeCycleState: LifecycleState get() = value[SourceState.lifeCycle] ?: LifecycleState.STOPPED

    public fun copy(block: MutableMeta.() -> Unit): SourceState = SourceState(value.copy(block))

    public companion object : MetaSpec() {
        public val sourceTimeOffset: MetaRef<String> by string()
        public val lifeCycle: MetaRef<LifecycleState> by enum<LifecycleState>()
    }
}


/**
 * An abstraction for a source of tag values
 */
public sealed interface TagSource : WithLifeCycle {
    /**
     * Read the current value of the tag from the source. Return null if the source is not open.
     * Throw an exception if the source is active, but read failed.
     */
    public suspend fun readTag(address: Meta): ValueWithTime<Meta>?

    /**
     * Read multiple values from the source. Return null if the source is not open. This method fails if any property read fails.
     */
    public suspend fun readMultipleTags(
        addresses: Collection<Pair<String, Meta>>
    ): Map<String, Result<ValueWithTime<Meta>>>? = addresses.associate {
        val value =  runCatching {  readTag(it.second) ?: return null}
        it.first to value
    }

    /**
     * Throw an exception if the column is not valid for this source
     */
    public fun validate(column: TagTableColumn)

    public val state: StateFlow<SourceState>

    /**
     * Establishes the connection according to source configuration if it is not already established and not on timeout.
     *
     * Should not suspend for a long time. Instead, it returns immediately after launching a connection job.
     * Does not ensure than connection is successful.
     */
    override suspend fun start()

}

