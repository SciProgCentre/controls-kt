package space.kscience.controls.misc

import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.Output
import kotlinx.datetime.Instant
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

    override fun readObject(input: Input): ValueWithTime<T> {
        val timestamp = InstantIOFormat.readObject(input)
        val value = valueFormat.readObject(input)
        return ValueWithTime(value, timestamp)
    }

    override fun writeObject(output: Output, obj: ValueWithTime<T>) {
        InstantIOFormat.writeObject(output, obj.time)
        valueFormat.writeObject(output, obj.value)
    }

}

private class ValueWithTimeMetaConverter<T>(
    val valueConverter: MetaConverter<T>,
) : MetaConverter<ValueWithTime<T>> {
    override fun metaToObject(
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