package space.kscience.controls.constructor.models

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.units.NumericAmount
import space.kscience.controls.constructor.units.UnitsOfMeasurement

/**
 *  A state describing a [T] value in the [range]
 */
public open class RangeState<T : Comparable<T>>(
    private val input: ValueState<T>,
    public val range: ClosedRange<T>,
) : ValueState<T> {

    override val value: T get() = input.value.coerceIn(range)

    override fun subscribe(): Flow<T>  = input.subscribe().map {
        it.coerceIn(range)
    }

    /**
     * A state showing that the range is on its lower boundary
     */
    public val atStart: ValueState<Boolean> = ValueState.map(input) { it <= range.start }

    /**
     * A state showing that the range is on its higher boundary
     */
    public val atEnd: ValueState<Boolean> = ValueState.map(input) { it >= range.endInclusive }

    override fun toString(): String = "DoubleRangeState(value=${value},range=$range)"
}

public class MutableRangeState<T : Comparable<T>>(
    private val mutableInput: MutableValueState<T>,
    range: ClosedRange<T>,
) : RangeState<T>(mutableInput, range), MutableValueState<T> {
    override var value: T
        get() = super.value
        set(value) {
            mutableInput.value = value.coerceIn(range)
        }

    override suspend fun emit(value: T) {
        mutableInput.emit(value.coerceIn(range))
    }
}

public fun <T : Comparable<T>> MutableRangeState(
    initialValue: T,
    range: ClosedRange<T>,
): MutableRangeState<T> = MutableRangeState<T>(MutableValueState(initialValue), range)

public fun <U : UnitsOfMeasurement> MutableRangeState(
    initialValue: Double,
    range: ClosedRange<Double>,
): MutableRangeState<NumericAmount<U>> = MutableRangeState(
    initialValue = NumericAmount(initialValue),
    range = NumericAmount<U>(range.start)..NumericAmount<U>(range.endInclusive)
)


public fun <T : Comparable<T>> ValueState<T>.coerceIn(
    range: ClosedRange<T>,
): RangeState<T> = RangeState(this, range)


public fun <T : Comparable<T>> MutableValueState<T>.coerceIn(
    range: ClosedRange<T>,
): MutableRangeState<T> = MutableRangeState(this, range)
