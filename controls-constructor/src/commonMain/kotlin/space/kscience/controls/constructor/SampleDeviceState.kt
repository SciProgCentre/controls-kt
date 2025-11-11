package space.kscience.controls.constructor

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import kotlin.time.Duration

@OptIn(FlowPreview::class)
public class SampleDeviceState<T>(
    public val origin: DeviceState<T>,
    public val interval: Duration,
) : DeviceState<T> {
    override val value: T by origin::value

    override fun subscribe(): Flow<T>  = origin.subscribe().sample(interval)

    override fun toString(): String = "SampleDeviceState($value, interval=$interval)"
}


public fun <T> DeviceState<T>.sample(interval: Duration): SampleDeviceState<T> = SampleDeviceState(this, interval)

@OptIn(FlowPreview::class)
public class MutableSampleDeviceState<T>(
    public val origin: MutableDeviceState<T>,
    public val interval: Duration,
) : MutableDeviceState<T> {
    override var value: T by origin::value

    override suspend fun emit(value: T) {
        origin.emit(value)
    }

    override fun subscribe(): Flow<T>  = origin.subscribe().sample(interval)

    override fun toString(): String = "SampleDeviceState($value, interval=$interval)"
}

public fun <T> MutableDeviceState<T>.sample(interval: Duration): MutableSampleDeviceState<T> = MutableSampleDeviceState(this, interval)