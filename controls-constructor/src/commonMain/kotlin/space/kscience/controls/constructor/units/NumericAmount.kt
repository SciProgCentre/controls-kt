package space.kscience.controls.constructor.units

import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.DeviceStateWithDependencies
import space.kscience.controls.constructor.map
import space.kscience.dataforge.meta.*
import kotlin.jvm.JvmInline


/**
 * A value without identity coupled to units of measurements.
 */
@JvmInline
public value class NumericAmount<U : UnitsOfMeasurement>(override val value: Double) : Amount<U> {

    override fun toString(): String  = value.toString()

    public companion object {

        private val zero: NumericAmount<Nothing> = NumericAmount(0.0)

        @Suppress("UNCHECKED_CAST")
        public fun <U : UnitsOfMeasurement> zero(): NumericAmount<U> = zero as NumericAmount<U>
    }
}

public fun <U : UnitsOfMeasurement> NumericAmount(
    number: Number,
): NumericAmount<U> = NumericAmount(number.toDouble())

public fun <U : UnitsOfMeasurement> Amount(
    number: Number,
): Amount<U> = NumericAmount(number.toDouble())

public operator fun <U : UnitsOfMeasurement> NumericAmount<U>.plus(
    other: NumericAmount<U>,
): NumericAmount<U> = NumericAmount(this.value + other.value)

public operator fun <U : UnitsOfMeasurement> NumericAmount<U>.minus(
    other: NumericAmount<U>,
): NumericAmount<U> = NumericAmount(this.value - other.value)

public operator fun <U : UnitsOfMeasurement> NumericAmount<U>.times(
    c: Number,
): NumericAmount<U> = NumericAmount(this.value * c.toDouble())

public operator fun <U : UnitsOfMeasurement> Number.times(
    value: NumericAmount<U>,
): NumericAmount<U> = NumericAmount(value.value * toDouble())

public operator fun <U : UnitsOfMeasurement> NumericAmount<U>.times(
    c: Double,
): NumericAmount<U> = NumericAmount(this.value * c)

public operator fun <U : UnitsOfMeasurement> NumericAmount<U>.div(
    c: Number,
): NumericAmount<U> = NumericAmount(this.value / c.toDouble())

public operator fun <U : UnitsOfMeasurement> NumericAmount<U>.div(other: NumericAmount<U>): Double =
    value / other.value

public operator fun <U : UnitsOfMeasurement> NumericAmount<U>.unaryMinus(): NumericAmount<U> = NumericAmount(-value)

public fun <U : UnitsOfMeasurement> Amount<U>.asNumeric(): NumericAmount<U> = this as? NumericAmount<U> ?: NumericAmount(value)

public fun <U : UnitsOfMeasurement> DeviceState<Amount<U>>.asNumeric(): DeviceStateWithDependencies<NumericAmount<U>> =
    DeviceState.map(this) { it.asNumeric() }


public fun <U : UnitsOfMeasurement> MetaConverter.Companion.numeric(
    units: U
): MetaConverter<NumericAmount<U>> = object : MetaConverter<NumericAmount<U>> {
    override fun readOrNull(source: Meta): NumericAmount<U>? {
        val unitsInSource = source["units"].string
        if (unitsInSource != null && unitsInSource != units.displayName) error("The units $unitsInSource do not match expected ${units.displayName}")
        val double = source.double ?: return null
        return NumericAmount<U>(double)
    }

    override fun convert(obj: NumericAmount<U>): Meta = Meta {
        double(obj.value)
        "units" put units.displayName
    }
}
