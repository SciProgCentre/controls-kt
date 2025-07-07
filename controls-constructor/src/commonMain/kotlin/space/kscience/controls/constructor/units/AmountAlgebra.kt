package space.kscience.controls.constructor.units

public interface AmountAlgebra<T : Amount<*>> {
    public operator fun T.plus(other: T): T
    public operator fun T.minus(other: T): T
    public operator fun T.unaryMinus(): T
    public operator fun T.times(scale: Number): T

    public operator fun Number.times(value: T): T = value * this

    public operator fun T.div(scale: Number): T
    public val zero: T
    public val one: T

    public operator fun T.compareTo(other: T): Int
}

public fun <T : Amount<*>> AmountAlgebra<T>.minOf(first: T, second: T): T = if (first < second) first else second

public fun <T : Amount<*>> AmountAlgebra<T>.maxOf(first: T, second: T): T = if (first > second) first else second

public fun <T : Amount<*>> AmountAlgebra<T>.sum(args: Iterable<T>): T = args.fold(zero) { acc, t -> acc + t }


private object NumericAmountAlgebra : AmountAlgebra<Numeric<Nothing>> {
    override fun Numeric<Nothing>.plus(other: Numeric<Nothing>): Numeric<Nothing> = Numeric(value + other.value)

    override fun Numeric<Nothing>.minus(other: Numeric<Nothing>): Numeric<Nothing> = Numeric(value - other.value)

    override fun Numeric<Nothing>.unaryMinus(): Numeric<Nothing> = Numeric(-value)

    override fun Numeric<Nothing>.times(scale: Number): Numeric<Nothing> = Numeric(value * scale.toDouble())

    override fun Numeric<Nothing>.div(scale: Number): Numeric<Nothing> = Numeric(value / scale.toDouble())

    override val zero: Numeric<Nothing> = Numeric(0.0)

    override val one: Numeric<Nothing> = Numeric(1.0)

    override fun Numeric<Nothing>.compareTo(other: Numeric<Nothing>): Int = value.compareTo(other.value)
}

@Suppress("UNCHECKED_CAST", "FunctionName")
public fun <U : UnitsOfMeasurement> NumericAmountAlgebra(): AmountAlgebra<Numeric<U>> = NumericAmountAlgebra as AmountAlgebra<Numeric<U>>