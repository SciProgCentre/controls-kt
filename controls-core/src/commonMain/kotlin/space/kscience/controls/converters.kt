package space.kscience.controls

import kotlinx.io.Sink
import kotlinx.io.Source
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.io.IOFormat
import space.kscience.dataforge.io.IOFormatFactory
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.Name
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlin.time.toDuration

public fun Number.asMeta(): Meta = Meta(asValue())

/**
 * Generate a nullable [MetaConverter] from non-nullable one
 */
public fun <T : Any> MetaConverter<T>.nullable(): MetaConverter<T?> = object : MetaConverter<T?> {
    override val descriptor: MetaDescriptor? get() = this@nullable.descriptor?.let { base ->
        MetaDescriptor {
            from(base)
            valueTypes = base.valueTypes?.let { (it + ValueType.NULL).distinct() }
        }
    }

    override fun convert(obj: T?): Meta = obj?.let { this@nullable.convert(it) } ?: Meta(Null)

    override fun readOrNull(source: Meta): T? = if (source.value == Null) null else this@nullable.readOrNull(source)

    override fun read(source: Meta): T? = if (source.value == Null) null else this@nullable.read(source)
}

//TODO to be moved to DF
private object DurationConverter : MetaConverter<Duration> {
    override fun readOrNull(source: Meta): Duration = when (source.value?.type) {
        null -> {
            val unit: DurationUnit = source["unit"].enum<DurationUnit>() ?: DurationUnit.SECONDS
            val value = source[Meta.VALUE_KEY].double ?: error("No value present for Duration")
            value.toDuration(unit)
        }
        ValueType.NUMBER -> source.double!!.toDuration(DurationUnit.SECONDS)

        ValueType.STRING -> Duration.parse(source.string!!)
        else -> error("Unsupported type for Duration: ${source.value?.type}")
    }

    override fun convert(obj: Duration): Meta = obj.toDouble(DurationUnit.SECONDS).asMeta()
}

public val MetaConverter.Companion.duration: MetaConverter<Duration> get() = DurationConverter


private object InstantConverter : MetaConverter<Instant> {
    override fun readOrNull(source: Meta): Instant? = source.string?.let { Instant.parse(it) }
    override fun convert(obj: Instant): Meta = Meta(obj.toString())
}

public val MetaConverter.Companion.instant: MetaConverter<Instant> get() = InstantConverter

public fun Instant.toMeta(): Meta = Meta(toString())

public val Meta?.instant: Instant? get() = this?.value?.string?.let { Instant.parse(it) }

/**
 * An [IOFormat] for [Instant]
 */
public object InstantIOFormat : IOFormat<Instant>, IOFormatFactory<Instant> {
    override fun build(context: Context, meta: Meta): IOFormat<Instant> = this

    override val name: Name = Name.of("instant")

    override val type: KType get() = typeOf<Instant>()

    override fun writeTo(sink: Sink, obj: Instant) {
        sink.writeLong(obj.epochSeconds)
        sink.writeInt(obj.nanosecondsOfSecond)
    }

    override fun readFrom(source: Source): Instant {
        val seconds = source.readLong()
        val nanoseconds = source.readInt()
        return Instant.fromEpochSeconds(seconds, nanoseconds)
    }
}


private object DoubleRangeConverter : MetaConverter<ClosedFloatingPointRange<Double>> {
    override fun readOrNull(source: Meta): ClosedFloatingPointRange<Double>? =
        source.value?.doubleArray?.let { (start, end) ->
            start..end
        }

    override fun convert(
        obj: ClosedFloatingPointRange<Double>,
    ): Meta = Meta(doubleArrayOf(obj.start, obj.endInclusive).asValue())
}

public val MetaConverter.Companion.doubleRange: MetaConverter<ClosedFloatingPointRange<Double>> get() = DoubleRangeConverter

public object UnitMetaConverter : MetaConverter<Unit> {

    override fun readOrNull(source: Meta): Unit = Unit

    override fun convert(obj: Unit): Meta = Meta.EMPTY
}

public val MetaConverter.Companion.unit: MetaConverter<Unit> get() = UnitMetaConverter
