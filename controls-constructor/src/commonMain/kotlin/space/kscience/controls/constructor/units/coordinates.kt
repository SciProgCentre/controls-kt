package space.kscience.controls.constructor.units

import kotlin.math.pow
import kotlin.math.sqrt

public data class XY<U : UnitsOfMeasurement>(val x: Numeric<U>, val y: Numeric<U>)

public fun <U : UnitsOfMeasurement> XY(x: Number, y: Number): XY<U> = XY(Numeric(x), Numeric((y)))

public operator fun <U : UnitsOfMeasurement> XY<U>.plus(other: XY<U>): XY<U> =
    XY(x + other.x, y + other.y)

public operator fun <U : UnitsOfMeasurement> XY<U>.times(c: Number): XY<U> = XY(x * c, y * c)
public operator fun <U : UnitsOfMeasurement> XY<U>.div(c: Number): XY<U> = XY(x / c, y / c)

public operator fun <U : UnitsOfMeasurement> XY<U>.unaryMinus(): XY<U> = XY(-x, -y)

public data class XYZ<U : UnitsOfMeasurement>(
    val x: Numeric<U>,
    val y: Numeric<U>,
    val z: Numeric<U>,
)

public val <U : UnitsOfMeasurement> XYZ<U>.length: Numeric<U>
    get() = Numeric(
        sqrt(x.value.pow(2) + y.value.pow(2) + z.value.pow(2))
    )

public fun <U : UnitsOfMeasurement> XYZ(x: Number, y: Number, z: Number): XYZ<U> =
    XYZ(Numeric(x), Numeric((y)), Numeric(z))

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
public fun <U : UnitsOfMeasurement, R : UnitsOfMeasurement> XYZ<U>.cast(units: R): XYZ<R> = this as XYZ<R>

public operator fun <U : UnitsOfMeasurement> XYZ<U>.plus(other: XYZ<U>): XYZ<U> =
    XYZ(x + other.x, y + other.y, z + other.z)

public operator fun <U : UnitsOfMeasurement> XYZ<U>.minus(other: XYZ<U>): XYZ<U> =
    XYZ(x - other.x, y - other.y, z - other.z)

public operator fun <U : UnitsOfMeasurement> XYZ<U>.times(c: Number): XYZ<U> = XYZ(x * c, y * c, z * c)
public operator fun <U : UnitsOfMeasurement> XYZ<U>.div(c: Number): XYZ<U> = XYZ(x / c, y / c, z / c)

public operator fun <U : UnitsOfMeasurement> XYZ<U>.unaryMinus(): XYZ<U> = XYZ(-x, -y, -z)