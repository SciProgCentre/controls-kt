package space.kscience.controls.constructor.units

public interface AmountAlgebra<U : UnitsOfMeasurement, T : Amount<U>> {
    public operator fun T.plus(other: T): T
    public operator fun T.minus(other: T): T
    public operator fun T.unaryMinus(): T
    public operator fun T.times(scale: Number): T

    public operator fun Number.times(value: T): T = value * this

    public operator fun T.div(scale: Number): T
    public val zero: T
    public val one: T

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

public fun <U : UnitsOfMeasurement, T : Amount<U>> AmountAlgebra<U, T>.minOf(first: T, second: T): T =
    if (first < second) first else second

public fun <U : UnitsOfMeasurement, T : Amount<U>> AmountAlgebra<U, T>.maxOf(first: T, second: T): T =
    if (first > second) first else second

public fun <U : UnitsOfMeasurement, T : Amount<U>> AmountAlgebra<U, T>.sum(args: Iterable<T>): T =
    args.fold(zero) { acc, t -> acc + t }

public open class NumericAmountAlgebra<U: UnitsOfMeasurement> : AmountAlgebra<U, Numeric<U>> {
    override fun Numeric<U>.plus(other: Numeric<U>): Numeric<U> = Numeric(value + other.value)

    override fun Numeric<U>.minus(other: Numeric<U>): Numeric<U> = Numeric(value - other.value)

    override fun Numeric<U>.unaryMinus(): Numeric<U> = Numeric(-value)

    override fun Numeric<U>.times(scale: Number): Numeric<U> = Numeric(value * scale.toDouble())

    override fun Numeric<U>.div(scale: Number): Numeric<U> = Numeric(value / scale.toDouble())

    override val zero: Numeric<U> = Numeric(0.0)

    override val one: Numeric<U> = Numeric(1.0)
}