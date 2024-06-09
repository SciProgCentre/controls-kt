package space.kscience.controls.demo.collective

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import space.kscience.controls.client.launchMagixService
import space.kscience.controls.constructor.ModelConstructor
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.onTimer
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.peer.PeerConnection
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.parseAsName
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.rsocket.rSocketWithWebSockets
import space.kscience.magix.server.startMagixServer
import space.kscience.maps.coordinates.*


internal data class CollectiveDeviceState(
    val id: CollectiveDeviceId,
    val configuration: CollectiveDeviceConfiguration,
    val position: MutableDeviceState<Gmc>,
    val velocity: MutableDeviceState<GmcVelocity>,
)

internal fun VirtualDeviceState(
    id: CollectiveDeviceId,
    position: Gmc,
    configuration: CollectiveDeviceConfiguration.() -> Unit = {},
) = CollectiveDeviceState(
    id,
    CollectiveDeviceConfiguration(id).apply(configuration),
    MutableDeviceState(position),
    MutableDeviceState(GmcVelocity.zero)
)


internal class DeviceCollectiveModel(
    context: Context,
    val deviceStates: Collection<CollectiveDeviceState>,
    val visibilityRange: Distance = 1.kilometers,
    val radioRange: Distance = 5.kilometers,
) : ModelConstructor(context), PeerConnection {

    /**
     * Propagate movement
     */
    private val movement = onTimer { prev, next ->
        val delta = (next - prev)
        deviceStates.forEach { state ->
            state.position.value = state.position.value.moveWith(state.velocity.value, delta)
        }
    }

    private fun locateVisible(id: CollectiveDeviceId): Map<CollectiveDeviceId, GmcCurve> {
        val coordinatesSnapshot = deviceStates.associate { it.id to it.position.value }

        val selected = coordinatesSnapshot[id] ?: error("Can't find device with id $id")

        val allCurves = coordinatesSnapshot
            .filterKeys { it != id }
            .mapValues { GeoEllipsoid.WGS84.curveBetween(selected, it.value) }

        return allCurves.filterValues { it.distance in 0.kilometers..visibilityRange }
    }

    val devices = deviceStates.associate {
        val device = CollectiveDeviceConstructor(
            context = context,
            configuration = it.configuration,
            position = it.position,
            velocity = it.velocity,
            peerConnection = this,
        ) {
            locateVisible(it.id)
        }
        //start movement program
        device.moveInCircles()
        it.id to device
    }

    val roster = deviceStates.associate { it.id to it.configuration }

    override suspend fun receive(address: String, contentId: String, requestMeta: Meta): Envelope {
        TODO("Not yet implemented")
    }

    override suspend fun send(address: String, envelope: Envelope, requestMeta: Meta) {
//        devices.values.filter { it.configuration.radioFrequency == address }.forEach { device ->
//            ```
//        }
    }
}

internal fun CoroutineScope.launchCollectiveMagixServer(
    collectiveModel: DeviceCollectiveModel,
): Job = launch(Dispatchers.IO) {
    val server = startMagixServer(
//        RSocketMagixFlowPlugin()
    )
    val deviceEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")

    collectiveModel.devices.forEach { (id, device) ->
        val deviceContext = collectiveModel.context.buildContext(id.parseAsName()) {
            coroutineContext(coroutineContext)
            plugin(DeviceManager)
        }

        deviceContext.install(id, device)

//        val deviceEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")

        deviceContext.request(DeviceManager).launchMagixService(deviceEndpoint, id)
    }
}