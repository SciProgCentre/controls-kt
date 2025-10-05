package space.kscience.controls.constructor.units

public interface AmountAlgebra<U : UnitsOfMeasurement, T : Amount<U>> {
    public operator fun T.plus(other: T): T
    public operator fun T.minus(other: T): T
    public operator fun T.unaryMinus(): T
    public operator fun T.times(scale: Number): T

    public operator fun Number.times(value: T): T = value * this

    public operator fun T.div(scale: Number): T
    public val zero: T

    public fun T.coerceValueIn(range: ClosedRange<Amount<U>>): T = when {
        value < range.start.value -> {
            val ratio = range.start.value / value
            this * ratio
        }

        value <= range.endInclusive.value -> this
        else -> {
            val ratio = range.endInclusive.value / value
            this * ratio
        }
    }
}

context(algebra: AmountAlgebra<U, T>)
public fun <U : UnitsOfMeasurement, T : Amount<U>> T.coerceValueAtMost(max: Amount<U>): T = with(algebra) {
    coerceValueIn(algebra.zero..max)
}

//bridge methods

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> T.plus(other: T): T = with(algebra) { this@plus + other }

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> T.minus(other: T): T = with(algebra) { this@minus - other }

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> T.unaryMinus(): T = with(algebra) { -this@unaryMinus }

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> T.times(scale: Number): T =
    with(algebra) { this@times * scale }

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> T.div(scale: Number): T =
    with(algebra) { this@div / scale }


public fun <U : UnitsOfMeasurement, T : Amount<U>> AmountAlgebra<U, T>.minOf(first: T, second: T): T =
    if (first < second) first else second

public fun <U : UnitsOfMeasurement, T : Amount<U>> AmountAlgebra<U, T>.maxOf(first: T, second: T): T =
    if (first > second) first else second

public fun <U : UnitsOfMeasurement, T : Amount<U>> AmountAlgebra<U, T>.sum(args: Iterable<T>): T =
    args.fold(zero) { acc, t -> acc + t }

public open class NumericAmountAlgebra<U : UnitsOfMeasurement> : AmountAlgebra<U, NumericAmount<U>> {
    override fun NumericAmount<U>.plus(other: NumericAmount<U>): NumericAmount<U> = NumericAmount(value + other.value)

    override fun NumericAmount<U>.minus(other: NumericAmount<U>): NumericAmount<U> = NumericAmount(value - other.value)

    override fun NumericAmount<U>.unaryMinus(): NumericAmount<U> = NumericAmount(-value)

    override fun NumericAmount<U>.times(scale: Number): NumericAmount<U> = NumericAmount(value * scale.toDouble())

    override fun NumericAmount<U>.div(scale: Number): NumericAmount<U> = NumericAmount(value / scale.toDouble())

    override val zero: NumericAmount<U> = NumericAmount(0.0)
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> T.coerceValueIn(
    algebra: AmountAlgebra<U, T>,
    range: ClosedRange<Amount<U>>
): T = with(algebra) { coerceValueIn(range) }