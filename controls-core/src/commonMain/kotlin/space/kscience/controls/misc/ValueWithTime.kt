package space.kscience.controls.misc

import kotlinx.datetime.Instant
import kotlinx.io.Sink
import kotlinx.io.Source
import space.kscience.dataforge.io.IOFormat
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A value coupled to a time it was obtained at
 */
public data class ValueWithTime<T>(val value: T, val time: Instant) {
    public companion object {
        /**
         * Create a [ValueWithTime] format for given value value [IOFormat]
         */
        public fun <T> ioFormat(
            valueFormat: IOFormat<T>,
        ): IOFormat<ValueWithTime<T>> = ValueWithTimeIOFormat(valueFormat)

        /**
         * Create a [MetaConverter] with time for given value [MetaConverter]
         */
        public fun <T> metaConverter(
            valueConverter: MetaConverter<T>,
        ): MetaConverter<ValueWithTime<T>> = ValueWithTimeMetaConverter(valueConverter)


        public const val META_TIME_KEY: String = "time"
        public const val META_VALUE_KEY: String = "value"
    }
}

private class ValueWithTimeIOFormat<T>(val valueFormat: IOFormat<T>) : IOFormat<ValueWithTime<T>> {
    override val type: KType get() = typeOf<ValueWithTime<T>>()


    override fun readFrom(source: Source): ValueWithTime<T> {
        val timestamp = InstantIOFormat.readFrom(source)
        val value = valueFormat.readFrom(source)
        return ValueWithTime(value, timestamp)
    }

    override fun writeTo(sink: Sink, obj: ValueWithTime<T>) {
        InstantIOFormat.writeTo(sink, obj.time)
        valueFormat.writeTo(sink, obj.value)
    }

}

private class ValueWithTimeMetaConverter<T>(
    val valueConverter: MetaConverter<T>,
) : MetaConverter<ValueWithTime<T>> {

    override val type: KType = typeOf<ValueWithTime<T>>()

    override fun metaToObjectOrNull(
        meta: Meta,
    ): ValueWithTime<T>? = valueConverter.metaToObject(meta[ValueWithTime.META_VALUE_KEY] ?: Meta.EMPTY)?.let {
        ValueWithTime(it, meta[ValueWithTime.META_TIME_KEY]?.instant ?: Instant.DISTANT_PAST)
    }

    override fun objectToMeta(obj: ValueWithTime<T>): Meta = Meta {
        ValueWithTime.META_TIME_KEY put obj.time.toMeta()
        ValueWithTime.META_VALUE_KEY put valueConverter.objectToMeta(obj.value)
    }
}

public fun <T : Any> MetaConverter<T>.withTime(): MetaConverter<ValueWithTime<T>> = ValueWithTimeMetaConverter(this)