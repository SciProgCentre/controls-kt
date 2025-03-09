package space.kscience.controls.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import space.kscience.controls.constructor.DeviceState
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


/**
 * Represent this [DeviceState] as Compose multiplatform [State]
 */
@Composable
public fun <T> DeviceState<T>.asComposeState(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): State<T> = valueFlow.collectAsState(value, coroutineContext)


/**
 * Represent this Compose [State] as [DeviceState]
 */
public fun <T> State<T>.asDeviceState(): DeviceState<T> = object : DeviceState<T> {
    override val value: T get() = this@asDeviceState.value

    override val valueFlow: Flow<T> get() = snapshotFlow { this@asDeviceState.value }

    override fun toString(): String = "ComposeState(value=$value)"
}