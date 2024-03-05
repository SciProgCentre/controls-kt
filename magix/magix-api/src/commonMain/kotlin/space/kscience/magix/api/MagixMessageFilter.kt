package space.kscience.magix.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.Serializable

/**
 * A filter that allows receiving only messages with format, origin and target in given list.
 */
@Serializable
public data class MagixMessageFilter(
    val format: Collection<String>? = null,
    val source: Collection<String>? = null,
    val target: Collection<String?>? = null,
) {

    public fun accepts(message: MagixMessage): Boolean =
        format?.contains(message.format) ?: true
                && source?.contains(message.sourceEndpoint) ?: true
                && target?.contains(message.targetEndpoint) ?: true

    public companion object {
        public val ALL: MagixMessageFilter = MagixMessageFilter()
    }
}

/**
 * Filter a [Flow] of messages based on given filter
 */
public fun Flow<MagixMessage>.filter(filter: MagixMessageFilter): Flow<MagixMessage> {
    if (filter == MagixMessageFilter.ALL) {
        return this
    }
    return filter(filter::accepts)
}