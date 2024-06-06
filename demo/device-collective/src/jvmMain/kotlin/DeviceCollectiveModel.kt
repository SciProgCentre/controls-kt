package space.kscience.controls.demo.map

import space.kscience.controls.constructor.ModelConstructor
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.onTimer
import space.kscience.dataforge.context.Context
import space.kscience.maps.coordinates.Gmc


typealias RemoteDeviceId = String


data class RemoteDeviceState(
    val id: RemoteDeviceId,
    val configuration: RemoteDeviceConfiguration,
    val position: MutableDeviceState<Gmc>,
    val velocity: MutableDeviceState<GmcVelocity>,
)

public fun RemoteDeviceState(
    id: RemoteDeviceId,
    position: Gmc,
    configuration: RemoteDeviceConfiguration.() -> Unit = {},
) = RemoteDeviceState(
    id,
    RemoteDeviceConfiguration(configuration),
    MutableDeviceState(position),
    MutableDeviceState(GmcVelocity.zero)
)


class DeviceCollectiveModel(
    context: Context,
    val deviceStates: Collection<RemoteDeviceState>,
) : ModelConstructor(context) {

    private val movement = onTimer { prev, next ->
        val delta = (next - prev)
        deviceStates.forEach { state ->
            state.position.value = state.position.value.moveWith(state.velocity.value, delta)
        }
    }
}