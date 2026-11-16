package space.kscience.controls.time

import space.kscience.dataforge.meta.MutableMetaDelegate
import space.kscience.dataforge.meta.MutableMetaProvider
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.asValue
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.number
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.meta.value
import space.kscience.dataforge.names.Name
import kotlin.time.Duration
import kotlin.time.Duration.Companion
import kotlin.time.Duration.Companion.parse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

//TODO remove after DataForge 0.13.0

/**
 * Creates a mutable metadata delegate for an [Instant] value.
 *
 * The value is read from the metadata entry identified by [key], or by the delegated property name when [key] is null.
 * Missing or null metadata values are replaced with the value produced by [default].
 * Numeric values are interpreted as epoch milliseconds, and string values are parsed as [Instant].
 * Boolean and list values are not supported and cause an error when read.
 * Written values are stored as string values using the [Instant] string representation.
 *
 * @param key optional metadata key to use instead of the delegated property name.
 * @param default provider of the value used when the metadata entry is missing or null.
 * @return a mutable delegate that reads and writes an [Instant] metadata value.
 */
public fun MutableMetaProvider.instant(
    key: Name? = null,
    default: () -> Instant
): MutableMetaDelegate<Instant> = value(
    key = key,
    writer = {
        it.toString().asValue()
    },
    reader = {
        when (it?.type) {
            null, ValueType.NULL -> default()
            ValueType.NUMBER -> Instant.fromEpochMilliseconds(it.number.toLong())
            ValueType.STRING -> Instant.parse(it.string)
            ValueType.BOOLEAN -> error("Instant cannot be represented as a boolean")
            ValueType.LIST -> error("Instant cannot be represented as a list")
        }
    }
)

/**
 * Creates a mutable delegate for a [Duration] value stored in this meta provider.
 *
 * The value is read from the specified [key], or from the delegated property name when [key] is null.
 * Missing or null values are resolved using [default]. Numeric values are interpreted as seconds,
 * and string values are parsed with [Duration.parse]. Boolean and list values are not supported
 * and cause an error when read.
 *
 * Written durations are stored as string values using their standard string representation.
 *
 * @param key The meta value name to bind the delegate to, or null to use the delegated property name.
 * @param default A function that provides the duration when the stored value is missing or null.
 * @return A mutable meta delegate that reads and writes [Duration] values.
 */
public fun MutableMetaProvider.duration(
    key: Name? = null,
    default: () -> Duration
): MutableMetaDelegate<Duration> = value(
    key = key,
    writer = {
        it.toString().asValue()
    },
    reader = {
        when (it?.type) {
            null, ValueType.NULL -> default()
            ValueType.NUMBER -> it.double.seconds
            ValueType.STRING -> Duration.parse(it.string)
            ValueType.BOOLEAN -> error("Duration cannot be represented as a boolean")
            ValueType.LIST -> error("Duration cannot be represented as a list")
        }
    }
)