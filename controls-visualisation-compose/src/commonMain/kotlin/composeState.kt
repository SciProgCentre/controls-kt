package space.kscience.controls.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import space.kscience.controls.constructor.DeviceState
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration


/**
 * Represent this [DeviceState] as Compose multiplatform [State]
 */
@OptIn(FlowPreview::class)
@Composable
public fun <T> DeviceState<T>.asComposeState(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    sampleInterval: Duration? = null
): State<T> = subscribe().let {
    if(sampleInterval != null){
        it.sample(sampleInterval)
    } else {
        it
    }
}.collectAsState(value, coroutineContext)


/**
 * Represent this Compose [State] as [DeviceState]
 */
public fun <T> State<T>.asDeviceState(): DeviceState<T> = object : DeviceState<T> {
    override val value: T get() = this@asDeviceState.value

    override fun subscribe(): Flow<T> = snapshotFlow { this@asDeviceState.value }

    override fun toString(): String = "ComposeState(value=$value)"
}