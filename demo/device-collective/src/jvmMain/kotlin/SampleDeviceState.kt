package space.kscience.controls.demo.collective

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.MutableDeviceState
import kotlin.time.Duration

@OptIn(FlowPreview::class)
class SampleDeviceState<T>(
    val origin: DeviceState<T>,
    val interval: Duration,
) : DeviceState<T> {
    override val value: T by origin::value
    override val valueFlow: Flow<T> get() = origin.valueFlow.sample(interval)

    override fun toString(): String = "DebounceDeviceState($value, interval=$interval)"
}


fun <T> DeviceState<T>.sample(interval: Duration) = SampleDeviceState(this, interval)

@OptIn(FlowPreview::class)
class MutableSampleDeviceState<T>(
    val origin: MutableDeviceState<T>,
    val interval: Duration,
) : MutableDeviceState<T> {
    override var value: T by origin::value
    override val valueFlow: Flow<T> get() = origin.valueFlow.sample(interval)

    override fun toString(): String = "DebounceDeviceState($value, interval=$interval)"
}

fun <T> MutableDeviceState<T>.sample(interval: Duration) = MutableSampleDeviceState(this, interval)