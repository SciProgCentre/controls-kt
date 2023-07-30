package space.kscience.magix.storage

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import space.kscience.magix.api.MagixFormat
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import kotlin.jvm.JvmInline

@Serializable
public sealed class MagixPayloadFilter {
    @SerialName("eq")
    public class Equals(public val path: String, public val value: JsonElement) : MagixPayloadFilter()

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


    @SerialName("not")
    public class Not(public val argument: MagixPayloadFilter) : MagixPayloadFilter()

    @SerialName("and")
    public class And(public val left: MagixPayloadFilter, public val right: MagixPayloadFilter) : MagixPayloadFilter()

    @SerialName("or")
    public class Or(public val left: MagixPayloadFilter, public val right: MagixPayloadFilter) : MagixPayloadFilter()
}

private fun JsonElement.takeElement(path: String): JsonElement? = if (path.isEmpty()) {
    this
} else {
    val separatorIndex = path.indexOf(".")
    if (separatorIndex == -1) {
        jsonObject[path]
    } else {
        val firstSegment = path.substring(0, separatorIndex)
        val remaining = path.substring(separatorIndex + 1, path.length)
        jsonObject[firstSegment]?.takeElement(remaining)
    }

}

public fun MagixPayloadFilter.test(element: JsonElement): Boolean = when (this) {
    is MagixPayloadFilter.Equals -> element.takeElement(path) == value
    is MagixPayloadFilter.DateTimeInRange -> TODO()
    is MagixPayloadFilter.NumberInRange -> TODO()
    is MagixPayloadFilter.Not -> !argument.test(element)
    is MagixPayloadFilter.And -> left.test(element) && right.test(element)
    is MagixPayloadFilter.Or -> left.test(element) || right.test(element)
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
     * @param payloadFilter filter for payload fields.
     * @param userFilter filters user names ("user.name").
     */
    public suspend fun useMessages(
        magixFilter: MagixMessageFilter? = null,
        payloadFilter: MagixPayloadFilter? = null,
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

