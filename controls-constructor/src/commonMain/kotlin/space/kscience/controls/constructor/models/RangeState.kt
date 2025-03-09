package space.kscience.controls.constructor.models

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.UnitsOfMeasurement

/**
 *  A state describing a [T] value in the [range]
 */
public open class RangeState<T : Comparable<T>>(
    private val input: DeviceState<T>,
    public val range: ClosedRange<T>,
) : DeviceState<T> {

    override val valueFlow: Flow<T> get() = input.valueFlow.map {
        it.coerceIn(range)
    }

    override val value: T get() = input.value.coerceIn(range)

    /**
     * A state showing that the range is on its lower boundary
     */
    public val atStart: DeviceState<Boolean> = input.map { it <= range.start }

    /**
     * A state showing that the range is on its higher boundary
     */
    public val atEnd: DeviceState<Boolean> = input.map { it >= range.endInclusive }

    override fun toString(): String = "DoubleRangeState(value=${value},range=$range)"
}

public class MutableRangeState<T : Comparable<T>>(
    private val mutableInput: MutableDeviceState<T>,
    range: ClosedRange<T>,
) : RangeState<T>(mutableInput, range), MutableDeviceState<T> {
    override var value: T
        get() = super.value
        set(value) {
            mutableInput.value = value.coerceIn(range)
        }
}

public fun <T : Comparable<T>> MutableRangeState(
    initialValue: T,
    range: ClosedRange<T>,
): MutableRangeState<T> = MutableRangeState<T>(MutableDeviceState(initialValue), range)

public fun <U : UnitsOfMeasurement> MutableRangeState(
    initialValue: Double,
    range: ClosedRange<Double>,
): MutableRangeState<NumericalValue<U>> = MutableRangeState(
    initialValue = NumericalValue(initialValue),
    range = NumericalValue<U>(range.start)..NumericalValue<U>(range.endInclusive)
)


public fun <T : Comparable<T>> DeviceState<T>.coerceIn(
    range: ClosedRange<T>,
): RangeState<T> = RangeState(this, range)


public fun <T : Comparable<T>> MutableDeviceState<T>.coerceIn(
    range: ClosedRange<T>,
): MutableRangeState<T> = MutableRangeState(this, range)
