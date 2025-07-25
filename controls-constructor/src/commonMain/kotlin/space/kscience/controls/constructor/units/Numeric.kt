package space.kscience.controls.constructor.units

import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.DeviceStateWithDependencies
import space.kscience.controls.constructor.map
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import kotlin.jvm.JvmInline


/**
 * A value without identity coupled to units of measurements.
 */
@JvmInline
public value class Numeric<U : UnitsOfMeasurement>(override val value: Double) : Amount<U> {
    public companion object {

        private val zero: Numeric<Nothing> = Numeric(0.0)

        @Suppress("UNCHECKED_CAST")
        public fun <U : UnitsOfMeasurement> zero(): Numeric<U> = zero as Numeric<U>
    }
}

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

public operator fun <U : UnitsOfMeasurement> Numeric<U>.unaryMinus(): Numeric<U> = Numeric(-value)

public fun <U : UnitsOfMeasurement> Amount<U>.asNumeric(): Numeric<U> = this as? Numeric<U> ?: Numeric(value)

public fun <U : UnitsOfMeasurement> DeviceState<Amount<U>>.asNumeric(): DeviceStateWithDependencies<Numeric<U>> = map { it.asNumeric() }


private object NumericalValueMetaConverter : MetaConverter<Numeric<*>> {
    override fun convert(obj: Numeric<*>): Meta = Meta(obj.value)

    override fun readOrNull(source: Meta): Numeric<*>? = source.double?.let { Numeric<Nothing>(it) }
}

@Suppress("UNCHECKED_CAST")
public fun <U : UnitsOfMeasurement> MetaConverter.Companion.numeric(): MetaConverter<Numeric<U>> =
    NumericalValueMetaConverter as MetaConverter<Numeric<U>>