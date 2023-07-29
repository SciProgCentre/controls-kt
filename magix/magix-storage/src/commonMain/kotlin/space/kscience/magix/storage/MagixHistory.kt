package space.kscience.magix.storage

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import space.kscience.magix.api.MagixFormat
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import kotlin.jvm.JvmInline

@Serializable
public sealed class MagixPayloadFilter {
    @SerialName("eq")
    public class Equals(public val path: String, public val value: JsonElement) : MagixPayloadFilter()

    @SerialName("not")
    public class Not(public val argument: MagixPayloadFilter) : MagixPayloadFilter()


//    @SerialName("like")
//    public class Like(public val path: String, public val value: String) : MagixPayloadFilter()

    @SerialName("numberInRange")
    public class NumberInRange(public val path: String, public val from: Number, public val to: Number) :
        MagixPayloadFilter()

    @SerialName("dateTimeInRange")
    public class DateTimeInRange(
        public val path: String,
        public val from: LocalDateTime,
        public val to: LocalDateTime,
    ) : MagixPayloadFilter()
}

public fun MagixPayloadFilter.test(element: JsonElement): Boolean {
    TODO()
//    when (this) {
//        is MagixPayloadFilter.DateTimeInRange -> TODO()
//        is MagixPayloadFilter.Equals -> TODO()
//        is MagixPayloadFilter.Not -> !(argument.test(element))
//        is MagixPayloadFilter.NumberInRange -> element.jsonObject[path]
//    }
}

public fun Sequence<JsonElement>.filter(magixPayloadFilter: MagixPayloadFilter): Sequence<JsonElement> = filter {
    magixPayloadFilter.test(it)
}


@Serializable
@JvmInline
public value class MagixUsernameFilter(public val userName: String)

/**
 * An interface for history access to magix messages
 */
public interface MagixHistory {
    /**
     * Find messages using intersection of given filters. If filters are not defined, get all messages.
     *
     * The result is supplied as a callback with [Sequence] of messages. If backing storage uses transactions, the function
     * closes all transactions after use.
     *
     * @param magixFilter magix header filter.
     * @param payloadFilters filters for payload fields.
     * @param userFilter filters user names ("user.name").
     */
    public suspend fun findMessages(
        magixFilter: MagixMessageFilter? = null,
        payloadFilters: List<MagixPayloadFilter> = emptyList(),
        userFilter: MagixUsernameFilter? = null,
        callback: (Sequence<MagixMessage>) -> Unit,
    )

    public companion object {
        public const val HISTORY_PAYLOAD_FORMAT: String = "magix.history"

        public val magixFormat: MagixFormat<MagixHistoryPayload> = MagixFormat(
            MagixHistoryPayload.serializer(),
            setOf(HISTORY_PAYLOAD_FORMAT)
        )
    }
}

