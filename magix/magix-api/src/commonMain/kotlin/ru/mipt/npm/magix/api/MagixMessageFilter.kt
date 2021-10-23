package ru.mipt.npm.magix.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.Serializable

@Serializable
public data class MagixMessageFilter(
    val format: List<String?>? = null,
    val origin: List<String?>? = null,
    val target: List<String?>? = null,
) {
    public companion object {
        public val ALL: MagixMessageFilter = MagixMessageFilter()
    }
}

/**
 * Filter a [Flow] of messages based on given filter
 */
public fun <T> Flow<MagixMessage<T>>.filter(filter: MagixMessageFilter): Flow<MagixMessage<T>> {
    if (filter == MagixMessageFilter.ALL) {
        return this
    }
    return filter { message ->
        filter.format?.contains(message.format) ?: true
                && filter.origin?.contains(message.origin) ?: true
                && filter.origin?.contains(message.origin) ?: true
                && filter.target?.contains(message.target) ?: true
    }
}