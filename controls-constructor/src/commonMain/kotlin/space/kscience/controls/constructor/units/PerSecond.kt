package space.kscience.controls.constructor.units

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Flow or speed unit
 */
@JvmInline
public value class PerSecond<U : UnitsOfMeasurement, T : Amount<U>>(public val valuePerSecond: T) : Amount<U> {
    override val value: Double get() = valuePerSecond.value

    public companion object {
        public fun <U : UnitsOfMeasurement> zero(): AmountPerSecond<U> = PerSecond(NumericAmount.zero())
    }
}

public val <U : UnitsOfMeasurement, T : Amount<U>> T.perSecond: PerSecond<U, T> get() = PerSecond(this)

public fun <U : UnitsOfMeasurement, T : Amount<U>> MetaConverter.Companion.perSecond(
    converter: MetaConverter<T>
): MetaConverter<PerSecond<U, T>> = object : MetaConverter<PerSecond<U, T>> {

    override fun readOrNull(source: Meta): PerSecond<U, T>? = converter.readOrNull(source)?.let { PerSecond(it) }

    override fun convert(obj: PerSecond<U, T>): Meta = converter.convert(obj.valuePerSecond)
}

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> PerSecond<U, T>.plus(
    other: PerSecond<U, T>
): PerSecond<U, T> = with(algebra) {
    PerSecond(valuePerSecond + other.valuePerSecond)
}

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> PerSecond<U, T>.minus(
    other: PerSecond<U, T>
): PerSecond<U, T> = with(algebra) {
    PerSecond(valuePerSecond - other.valuePerSecond)
}

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> PerSecond<U, T>.unaryMinus(): PerSecond<U, T> =
    with(algebra) {
        PerSecond(-valuePerSecond)
    }

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> PerSecond<U, T>.times(
    scale: Number
): PerSecond<U, T> = with(algebra) {
    PerSecond(valuePerSecond * scale.toDouble())
}

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> PerSecond<U, T>.div(scale: Number): PerSecond<U, T> =
    with(algebra) {
        PerSecond(valuePerSecond / scale.toDouble())
    }

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> T.div(duration: Duration): PerSecond<U, T> = with(algebra) {
    PerSecond(this@div / duration.toDouble(DurationUnit.SECONDS))
}

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> PerSecond<U, T>.times(
    duration: Duration
): T = with(algebra) { valuePerSecond * duration.toDouble(DurationUnit.SECONDS) }

context(algebra: AmountAlgebra<U, T>)
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> Duration.times(
    materialFlow: PerSecond<U, T>
): T = with(algebra) { materialFlow.valuePerSecond * toDouble(DurationUnit.SECONDS) }


context(algebra: AmountAlgebra<U, T>)
public fun <U : UnitsOfMeasurement, T : Amount<U>> PerSecond<U, T>.coerceValueIn(
    range: ClosedRange<Amount<U>>
): PerSecond<U, T> = with(algebra) {
    PerSecond(valuePerSecond.coerceValueIn(range))
}

public fun <U : UnitsOfMeasurement> AmountPerSecond<U>.coerceValueIn(
    range: ClosedRange<Amount<U>>
): AmountPerSecond<U> = AmountPerSecond(valuePerSecond.value.coerceIn(range.start.value, range.endInclusive.value))

context(algebra: AmountAlgebra<U, T>)
public fun <U : UnitsOfMeasurement, T : Amount<U>> PerSecond<U, T>.coerceValueAtMost(max: Amount<U>): PerSecond<U, T> =
    coerceValueIn(algebra.zero..max)

public fun <U : UnitsOfMeasurement> AmountPerSecond<U>.coerceValueAtMost(max: Amount<U>): AmountPerSecond<U> =
    AmountPerSecond(valuePerSecond.value.coerceAtMost(max.value))

context(algebra: AmountAlgebra<U, T>)
public fun <U : UnitsOfMeasurement, T : Amount<U>> sum(args: Iterable<PerSecond<U, T>>): PerSecond<U, T> =
    with(algebra) {
        PerSecond(args.fold(zero) { acc, t -> acc + t.valuePerSecond })
    }

context(algebra: AmountAlgebra<U, T>)
public fun <U : UnitsOfMeasurement, T : Amount<U>> minOf(
    first: PerSecond<U, T>,
    second: PerSecond<U, T>
): PerSecond<U, T> = if (first < second) first else second

context(algebra: AmountAlgebra<U, T>)
public fun <U : UnitsOfMeasurement, T : Amount<U>> maxOf(
    first: PerSecond<U, T>,
    second: PerSecond<U, T>
): PerSecond<U, T> = if (first > second) first else second

public typealias AmountPerSecond<U> = PerSecond<U, NumericAmount<U>>

public fun <U : UnitsOfMeasurement> AmountPerSecond(value: Number): AmountPerSecond<U> =
    PerSecond<U, NumericAmount<U>>(NumericAmount(value))
