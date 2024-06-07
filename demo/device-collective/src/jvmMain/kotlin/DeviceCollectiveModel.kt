package space.kscience.controls.demo.collective

import space.kscience.controls.constructor.ModelConstructor
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.onTimer
import space.kscience.dataforge.context.Context
import space.kscience.maps.coordinates.*


typealias DeviceId = String


internal data class VirtualDeviceState(
    val id: DeviceId,
    val configuration: CollectiveDeviceConfiguration,
    val position: MutableDeviceState<Gmc>,
    val velocity: MutableDeviceState<GmcVelocity>,
)

internal fun VirtualDeviceState(
    id: DeviceId,
    position: Gmc,
    configuration: CollectiveDeviceConfiguration.() -> Unit = {},
) = VirtualDeviceState(
    id,
    CollectiveDeviceConfiguration(id).apply(configuration),
    MutableDeviceState(position),
    MutableDeviceState(GmcVelocity.zero)
)


internal class DeviceCollectiveModel(
    context: Context,
    val deviceStates: Collection<VirtualDeviceState>,
    val visibilityRange: Distance,
) : ModelConstructor(context) {

    /**
     * Propagate movement
     */
    private val movement = onTimer { prev, next ->
        val delta = (next - prev)
        deviceStates.forEach { state ->
            state.position.value = state.position.value.moveWith(state.velocity.value, delta)
        }
    }

    suspend fun locateVisible(id: DeviceId): Map<DeviceId, GmcCurve> {
        val coordinatesSnapshot = deviceStates.associate { it.id to it.position.value }

        val selected = coordinatesSnapshot[id] ?: error("Can't find device with id $id")

        val allCurves = coordinatesSnapshot
            .filterKeys { it != id }
            .mapValues { GeoEllipsoid.WGS84.curveBetween(selected, it.value) }

        return allCurves.filterValues { it.distance in 0.kilometers..visibilityRange }
    }
}