package space.kscience.controls.constructor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import space.kscience.dataforge.meta.MetaConverter


/**
 *  A state describing a [Double] value in the [range]
 */
public class DoubleRangeState(
    initialValue: Double,
    public val range: ClosedFloatingPointRange<Double>,
) : MutableDeviceState<Double> {

    init {
        require(initialValue in range) { "Initial value should be in range" }
    }

    override val converter: MetaConverter<Double> = MetaConverter.double

    private val _valueFlow = MutableStateFlow(initialValue)

    override var value: Double
        get() = _valueFlow.value
        set(newValue) {
            _valueFlow.value = newValue.coerceIn(range)
        }

    override val valueFlow: StateFlow<Double> get() = _valueFlow

    /**
     * A state showing that the range is on its lower boundary
     */
    public val atStartState: DeviceState<Boolean> = map(MetaConverter.boolean) { it <= range.start }

    /**
     * A state showing that the range is on its higher boundary
     */
    public val atEndState: DeviceState<Boolean> = map(MetaConverter.boolean) { it >= range.endInclusive }
}

@Suppress("UnusedReceiverParameter")
public fun DeviceGroup.rangeState(
    initialValue: Double,
    range: ClosedFloatingPointRange<Double>,
): DoubleRangeState = DoubleRangeState(initialValue, range)