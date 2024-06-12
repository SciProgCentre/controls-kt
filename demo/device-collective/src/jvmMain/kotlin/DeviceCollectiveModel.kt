package space.kscience.controls.demo.collective

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.client.launchMagixService
import space.kscience.controls.constructor.ModelConstructor
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.onTimer
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.manager.respondMessage
import space.kscience.controls.peer.PeerConnection
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.toByteArray
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

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

internal class DeviceCollectiveModel(
    context: Context,
    val deviceStates: Collection<CollectiveDeviceState>,
    val visibilityRange: Distance = 0.5.kilometers,
    val radioRange: Distance = 5.kilometers,
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

    private fun locateVisible(id: CollectiveDeviceId): Map<CollectiveDeviceId, GmcCurve> {
        val coordinatesSnapshot = deviceStates.associate { it.id to it.position.value }

        val selected = coordinatesSnapshot[id] ?: error("Can't find device with id $id")

        val allCurves = coordinatesSnapshot
            .filterKeys { it != id }
            .mapValues { GeoEllipsoid.WGS84.curveBetween(selected, it.value) }

        return allCurves.filterValues { it.distance in 0.kilometers..visibilityRange }
    }

    inner class RadioPeerConnection(private val peerState: CollectiveDeviceState) : PeerConnection {
        override suspend fun receive(address: String, contentId: String, requestMeta: Meta): Envelope? = null

        override suspend fun send(address: String, envelope: Envelope, requestMeta: Meta) {
            devices.filter { it.value.configuration.radioFrequency == address }.filter {
                GeoEllipsoid.WGS84.curveBetween(peerState.position.value, it.value.position.value).distance < radioRange
            }.forEach { (id, target) ->
                check(envelope.data != null) { "Envelope data is empty" }
                val message = json.decodeFromString(
                    DeviceMessage.serializer(),
                    envelope.data?.toByteArray()?.decodeToString() ?: ""
                )
                target.respondMessage(id.parseAsName(), message)
            }
        }
    }

    val devices = deviceStates.associate {
        val device = CollectiveDeviceConstructor(
            context = context,
            configuration = it.configuration,
            position = it.position,
            velocity = it.velocity,
            peerConnection = RadioPeerConnection(it),
        ) {
            locateVisible(it.id)
        }
        it.id to device
    }

    val roster = deviceStates.associate { it.id to it.configuration }


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