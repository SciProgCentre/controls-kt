package space.kscience.controls.constructor

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import space.kscience.controls.time.ValueWithTime
import kotlin.time.Duration

@OptIn(FlowPreview::class)
public class SampleValueState<T>(
    public val origin: ValueState<T>,
    public val interval: Duration,
) : ValueState<T> {
    override val value: T by origin::value

    override val valueWithTime: ValueWithTime<T> by origin::valueWithTime

    override fun subscribe(): Flow<T>  = origin.subscribe().sample(interval)

    override fun subscribeWithTime(): Flow<ValueWithTime<T>> =origin.subscribeWithTime().sample(interval)

    override fun toString(): String = "SampleDeviceState($value, interval=$interval)"
}


public fun <T> ValueState<T>.sample(interval: Duration): SampleValueState<T> = SampleValueState(this, interval)

@OptIn(FlowPreview::class)
public class MutableSampleValueState<T>(
    public val origin: MutableValueState<T>,
    public val interval: Duration,
) : MutableValueState<T> {
    override var value: T by origin::value

    override val valueWithTime: ValueWithTime<T> by origin::valueWithTime

    override suspend fun emit(value: T) {
        origin.emit(value)
    }

    override fun subscribe(): Flow<T>  = origin.subscribe().sample(interval)

    override fun subscribeWithTime(): Flow<ValueWithTime<T>> = origin.subscribeWithTime().sample(interval)

    override fun toString(): String = "SampleDeviceState($value, interval=$interval)"
}

public fun <T> MutableValueState<T>.sample(interval: Duration): MutableSampleValueState<T> = MutableSampleValueState(this, interval)