package space.kscience.controls.demo.collective

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.sample
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.MutableDeviceState
import kotlin.time.Duration

@OptIn(FlowPreview::class)
class DebounceDeviceState<T>(
    val origin: DeviceState<T>,
    val interval: Duration,
) : DeviceState<T> {
    override val value: T by origin::value
    override val valueFlow: Flow<T> get() = origin.valueFlow.debounce(interval)

    override fun toString(): String = "DebounceDeviceState($value, interval=$interval)"
}


fun <T> DeviceState<T>.debounce(interval: Duration) = DebounceDeviceState(this, interval)

@OptIn(FlowPreview::class)
class MutableDebounceDeviceState<T>(
    val origin: MutableDeviceState<T>,
    val interval: Duration,
) : MutableDeviceState<T> {
    override var value: T by origin::value

    override suspend fun emit(value: T) {
        origin.emit(value)
    }

    override val valueFlow: Flow<T> get() = origin.valueFlow.sample(interval)

    override fun toString(): String = "DebounceDeviceState($value, interval=$interval)"
}

fun <T> MutableDeviceState<T>.debounce(interval: Duration) = MutableDebounceDeviceState(this, interval)