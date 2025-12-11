package space.kscience.controls.demo.collective

import kotlinx.coroutines.*
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertySetMessage
import space.kscience.controls.client.DeviceClient
import space.kscience.controls.client.launchMagixService
import space.kscience.controls.client.write
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.manager.respondMessage
import space.kscience.controls.peer.PeerConnection
import space.kscience.controls.spec.name
import space.kscience.controls.spec.write
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.toByteArray
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.parseAsName
import space.kscience.kmath.geometry.degrees
import space.kscience.kmath.geometry.radians
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.rsocket.rSocketWithWebSockets
import space.kscience.magix.server.startMagixServer
import space.kscience.maps.coordinates.*
import kotlin.math.PI
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


private val deviceVelocity = 0.1.kilometers

private val center = Gmc.ofDegrees(55.925, 37.514)
private val radius = 0.01.degrees

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

internal data class CollectiveDeviceState(
    val id: CollectiveDeviceId,
    val configuration: CollectiveDeviceConfiguration,
    val position: MutableValueState<Gmc>,
    val velocity: MutableValueState<GmcVelocity>,
)

internal fun CollectiveDeviceState(
    id: CollectiveDeviceId,
    position: Gmc,
    configuration: CollectiveDeviceConfiguration.() -> Unit = {},
) = CollectiveDeviceState(
    id,
    CollectiveDeviceConfiguration(id).apply(configuration),
    MutableValueState(position),
    MutableValueState(GmcVelocity.zero)
)

internal class DeviceCollectiveModel(
    context: Context,
    val deviceStates: Collection<CollectiveDeviceState>,
    val visibilityRange: Distance = 0.5.kilometers,
    val radioRange: Distance = 1.kilometers,
) : ModelConstructor(context) {

    /**
     * Propagate movement
     */
    private val movement = onTimer(200.milliseconds) { prev, next ->
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

    inner class RadioPeerConnectionModel(private val position: ValueState<Gmc>) : PeerConnection {
        override suspend fun receive(address: String, contentId: String, requestMeta: Meta): Envelope? = null

        override suspend fun send(address: String, envelope: Envelope, requestMeta: Meta) {
            devices.values.filter { it.configuration.radioFrequency == address }.filter {
                GeoEllipsoid.WGS84.curveBetween(position.value, it.position.value).distance < radioRange
            }.forEach { target ->
                check(envelope.data != null) { "Envelope data is empty" }
                val message = json.decodeFromString(
                    DeviceMessage.serializer(),
                    envelope.data?.toByteArray()?.decodeToString() ?: ""
                )
                target.respondMessage(target.configuration.deviceId.parseAsName(), message)
            }
        }
    }

    val devices = deviceStates.associate { state ->
        val device = CollectiveDeviceConstructor(
            context = context,
            configuration = state.configuration,
            position = state.position,
            velocity = state.velocity,
            peerConnection = RadioPeerConnectionModel(state.position),
        ) {
            locateVisible(state.id)
        }
        state.id to device
    }

    internal fun createTrawler(position: Gmc, id: CollectiveDeviceId = "trawler"): CollectiveDeviceConstructor {
        val state = CollectiveDeviceState(
            id = id,
            configuration = CollectiveDeviceConfiguration(id),
            position = MutableValueState(position),
            velocity = MutableValueState(GmcVelocity.zero)
        )

        val result = CollectiveDeviceConstructor(
            context = context,
            configuration = state.configuration,
            position = state.position,
            velocity = state.velocity,
            peerConnection = RadioPeerConnectionModel(state.position),
        ) {
            locateVisible(state.id)
        }

        // TODO move to CollectiveDeviceState
        onTimer(200.milliseconds) { prev, next ->
            val delta = (next - prev)
            require(delta >= Duration.ZERO) { "Negative time change" }

            state.position.value = state.position.value.moveWith(state.velocity.value, delta)
        }

        result.onTimer(1.seconds) {
            val envelope = Envelope {
                data {
                    writeString(
                        json.encodeToString(
                            DeviceMessage.serializer(),
                            PropertySetMessage(
                                time = clock.now(),
                                property = CollectiveDevice.velocity.name,
                                value = gmcVelocityMetaConverter.convert(state.velocity.value),
                                targetDevice = null
                            )
                        )
                    )
                }
            }

            result.peerConnection.send(
                CollectiveDeviceConfiguration.DEFAULT_FREQUENCY,
                envelope
            )
        }

        return result
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


internal fun generateModel(
    context: Context,
    size: Int = 50,
    reportInterval: Duration = 500.milliseconds,
    additionalConfiguration: CollectiveDeviceConfiguration.() -> Unit = {},
): DeviceCollectiveModel {
    val devices: List<CollectiveDeviceState> = List(size) { index ->
        val id = "device[$index]"

        CollectiveDeviceState(
            id = id,
            Gmc(
                center.latitude + radius * Random.nextDouble(),
                center.longitude + radius * Random.nextDouble()
            )
        ) {
            deviceId = id
            description = "Virtual remote device $id"
            this.reportInterval = reportInterval.inWholeMilliseconds.toInt()
            additionalConfiguration()
        }
    }

    val model = DeviceCollectiveModel(context, devices)

    return model
}

fun DeviceClient.moveInCircles(scope: CoroutineScope = this): Job = scope.launch {
    var bearing = Random.nextDouble(-PI, PI).radians
    write(CollectiveDevice.velocity, GmcVelocity(bearing, deviceVelocity))
    while (isActive) {
        delay(500)
        bearing += 5.degrees
        write(CollectiveDevice.velocity, GmcVelocity(bearing, deviceVelocity))
    }
}


internal fun CollectiveDeviceConstructor.moveTo(
    targetPosition: Gmc,
    speedLimit: Distance = deviceVelocity,
    scope: CoroutineScope = this,
): Job = scope.launch {
    do {
        val curve = GeoEllipsoid.WGS84.curveBetween(position.value, targetPosition)
        write(CollectiveDevice.velocity, GmcVelocity(curve.forward.bearing, speedLimit))
        delay(1.seconds)
    } while (curve.distance > 0.1.kilometers)
    write(CollectiveDevice.velocity, GmcVelocity.zero)

}