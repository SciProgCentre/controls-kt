package space.kscience.controls.constructor.units

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import kotlin.jvm.JvmInline


public interface Amount<U : UnitsOfMeasurement> : Comparable<NumericalValue<U>>{
    public val value: Double

    override fun compareTo(other: NumericalValue<U>): Int = value.compareTo(other.value)
}

/**
 * A value without identity coupled to units of measurements.
 */
@JvmInline
public value class NumericalValue<U : UnitsOfMeasurement>(override val value: Double) : Amount<U>

public typealias NV<U> = NumericalValue<U>

public fun <U : UnitsOfMeasurement> NumericalValue(
    number: Number,
): NumericalValue<U> = NumericalValue(number.toDouble())

public fun <U : UnitsOfMeasurement> Amount(
    number: Number,
): Amount<U> = NumericalValue(number.toDouble())

public operator fun <U : UnitsOfMeasurement> NumericalValue<U>.plus(
    other: NumericalValue<U>,
): NumericalValue<U> = NumericalValue(this.value + other.value)

public operator fun <U : UnitsOfMeasurement> NumericalValue<U>.minus(
    other: NumericalValue<U>,
): NumericalValue<U> = NumericalValue(this.value - other.value)

public operator fun <U : UnitsOfMeasurement> NumericalValue<U>.times(
    c: Number,
): NumericalValue<U> = NumericalValue(this.value * c.toDouble())

public operator fun <U : UnitsOfMeasurement> Number.times(
    numericalValue: NumericalValue<U>,
): NumericalValue<U> = NumericalValue(numericalValue.value * toDouble())

public operator fun <U : UnitsOfMeasurement> NumericalValue<U>.times(
    c: Double,
): NumericalValue<U> = NumericalValue(this.value * c)

public operator fun <U : UnitsOfMeasurement> NumericalValue<U>.div(
    c: Number,
): NumericalValue<U> = NumericalValue(this.value / c.toDouble())

public operator fun <U : UnitsOfMeasurement> NumericalValue<U>.div(other: NumericalValue<U>): Double =
    value / other.value

public operator fun <U: UnitsOfMeasurement> NumericalValue<U>.unaryMinus(): NumericalValue<U> = NumericalValue(-value)


private object NumericalValueMetaConverter : MetaConverter<NumericalValue<*>> {
    override fun convert(obj: NumericalValue<*>): Meta = Meta(obj.value)

    override fun readOrNull(source: Meta): NumericalValue<*>? = source.double?.let { NumericalValue<Nothing>(it) }
}

@Suppress("UNCHECKED_CAST")
public fun <U : UnitsOfMeasurement> MetaConverter.Companion.numerical(): MetaConverter<NumericalValue<U>> =
    NumericalValueMetaConverter as MetaConverter<NumericalValue<U>>