package space.kscience.controls.constructor

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import space.kscience.controls.time.ValueWithTime
import kotlin.time.Duration

/**
 * A [ValueState] wrapper that samples updates emitted by another [ValueState].
 *
 * The current [ValueState.value] and [ValueState.valueWithTime] are delegated directly to [origin],
 * so synchronous reads always reflect the latest value available from the wrapped state. Subscriptions
 * returned by [subscribe] and [subscribeWithTime] apply flow sampling with [interval], emitting the
 * most recent upstream update observed during each sampling period.
 *
 * This wrapper reduces the frequency of observed change notifications without modifying the source
 * state or the value returned by direct reads.
 *
 * @param T the type of value held by the state.
 */
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

/**
 * A mutable value state wrapper that delegates value storage and updates to another mutable state
 * while sampling subscription flows at a fixed time interval.
 *
 * Direct reads, writes, timestamped value access, and emitted updates are forwarded to the wrapped
 * state without sampling. Sampling is applied only to values observed through subscription methods,
 * so rapid consecutive updates from the wrapped state may be coalesced and only the latest value
 * available for each sampling period is emitted to subscribers.
 *
 * @param T the type of value held by the state
 */
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