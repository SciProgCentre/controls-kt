package space.kscience.magix.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter

/**
 * Base class for history API request and response messages
 */
@Serializable
public sealed class MagixHistoryPayload

/**
 * Message to request history information from the storage
 *
 * @param magixFilter filter for magix headers
 * @param payloadFilters filter for payload fields
 * @param userFilter filter for user name
 * @param pageSize if defined, defines the maximum number of messages per response message. If not defined, uses history provider default.
 */
@Serializable
@SerialName("history.request")
public data class HistoryRequestPayload(
    val magixFilter: MagixMessageFilter? = null,
    val payloadFilter: MagixPayloadFilter? = null,
    val userFilter: MagixUsernameFilter? = null,
    val pageSize: Int? = null
) : MagixHistoryPayload()

/**
 * A response to a [HistoryRequestPayload]. Contains a list of messages.
 *
 * @param messages the list of messages.
 * @param page the index of current page for multiple page messages. Page indexing starts with 0.
 * @param lastPage true if this page is the last.
 */
@Serializable
@SerialName("history.response")
public data class HistoryResponsePayload(
    val messages: List<MagixMessage>,
    val page: Int = 0,
    val lastPage: Boolean = true
) : MagixHistoryPayload()