package space.kscience.controls.constructor.units

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import kotlin.jvm.JvmInline


/**
 * A value without identity coupled to units of measurements.
 */
@JvmInline
public value class Numeric<U : UnitsOfMeasurement>(override val value: Double) : Amount<U>

public fun <U : UnitsOfMeasurement> Numeric(
    number: Number,
): Numeric<U> = Numeric(number.toDouble())

public fun <U : UnitsOfMeasurement> Amount(
    number: Number,
): Amount<U> = Numeric(number.toDouble())

public operator fun <U : UnitsOfMeasurement> Numeric<U>.plus(
    other: Numeric<U>,
): Numeric<U> = Numeric(this.value + other.value)

public operator fun <U : UnitsOfMeasurement> Numeric<U>.minus(
    other: Numeric<U>,
): Numeric<U> = Numeric(this.value - other.value)

public operator fun <U : UnitsOfMeasurement> Numeric<U>.times(
    c: Number,
): Numeric<U> = Numeric(this.value * c.toDouble())

public operator fun <U : UnitsOfMeasurement> Number.times(
    value: Numeric<U>,
): Numeric<U> = Numeric(value.value * toDouble())

public operator fun <U : UnitsOfMeasurement> Numeric<U>.times(
    c: Double,
): Numeric<U> = Numeric(this.value * c)

public operator fun <U : UnitsOfMeasurement> Numeric<U>.div(
    c: Number,
): Numeric<U> = Numeric(this.value / c.toDouble())

public operator fun <U : UnitsOfMeasurement> Numeric<U>.div(other: Numeric<U>): Double =
    value / other.value

public operator fun <U: UnitsOfMeasurement> Numeric<U>.unaryMinus(): Numeric<U> = Numeric(-value)


private object NumericalValueMetaConverter : MetaConverter<Numeric<*>> {
    override fun convert(obj: Numeric<*>): Meta = Meta(obj.value)

    override fun readOrNull(source: Meta): Numeric<*>? = source.double?.let { Numeric<Nothing>(it) }
}

@Suppress("UNCHECKED_CAST")
public fun <U : UnitsOfMeasurement> MetaConverter.Companion.numericalValue(): MetaConverter<Numeric<U>> =
    NumericalValueMetaConverter as MetaConverter<Numeric<U>>