package space.kscience.controls.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.time.ValueWithTime
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration


/**
 * Represent this [ValueState] as Compose multiplatform [State]
 */
@OptIn(FlowPreview::class)
@Composable
public fun <T> ValueState<T>.asComposeState(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    sampleInterval: Duration? = null
): State<T> = subscribe().let {
    if (sampleInterval != null) {
        it.sample(sampleInterval)
    } else {
        it
    }
}.collectAsState(value, coroutineContext)


/**
 * Represent this Compose [State] as [ValueState]
 */
public fun <T> State<T>.asDeviceState(clock: Clock = Clock.System): ValueState<T> = object : ValueState<T> {
    override val value: T get() = this@asDeviceState.value

    override val valueWithTime: ValueWithTime<T> get() = ValueWithTime(value, clock.now())

    override fun subscribe(): Flow<T> = snapshotFlow { this@asDeviceState.value }

    override fun subscribeWithTime(): Flow<ValueWithTime<T>> = snapshotFlow { ValueWithTime(value, clock.now()) }

    override fun toString(): String = "ComposeState(value=$value)"
}