@file:OptIn(DFExperimental::class)

package space.kscience.controls.demo.collective

import space.kscience.controls.api.Device
import space.kscience.controls.constructor.*
import space.kscience.controls.peer.PeerConnection
import space.kscience.controls.spec.DeviceSpec
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.Scheme
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.maps.coordinates.Gmc
import space.kscience.maps.coordinates.GmcCurve
import kotlin.time.Duration.Companion.milliseconds

typealias CollectiveDeviceId = String

class CollectiveDeviceConfiguration(deviceId: CollectiveDeviceId) : Scheme() {
    var deviceId by string(deviceId)
    var description by string()
    var reportInterval by int(500)
    var radioFrequency by string(default = DEFAULT_FREQUENCY)

    companion object {
        const val DEFAULT_FREQUENCY = "169 MHz"
    }
}

typealias CollectiveDeviceRoster = Map<CollectiveDeviceId, CollectiveDeviceConfiguration>

interface CollectiveDevice : Device {

    public val id: CollectiveDeviceId

    public val peerConnection: PeerConnection

    suspend fun getPosition(): Gmc

    suspend fun getVelocity(): GmcVelocity

    suspend fun setVelocity(value: GmcVelocity)

    suspend fun listVisible(): Collection<CollectiveDeviceId>

    companion object : DeviceSpec<CollectiveDevice>() {
        val position by property<Gmc>(
            converter = MetaConverter.serializable(),
            read = { getPosition() }
        )

        val velocity by mutableProperty<GmcVelocity>(
            converter = MetaConverter.serializable(),
            read = { getVelocity() },
            write = { _, value -> setVelocity(value) }
        )

        val visibleNeighbors by property(
            MetaConverter.stringList,
            read = {
                listVisible().toList()
            }
        )

//        val listVisible by action(MetaConverter.unit, MetaConverter.valueList<String> { it.string }) {
//            listVisible().toList()
//        }
    }
}


class CollectiveDeviceConstructor(
    context: Context,
    val configuration: CollectiveDeviceConfiguration,
    position: MutableDeviceState<Gmc>,
    velocity: MutableDeviceState<GmcVelocity>,
    override val peerConnection: PeerConnection,
    private val observation: suspend () -> Map<CollectiveDeviceId, GmcCurve>,
) : DeviceConstructor(context, configuration.meta), CollectiveDevice {

    override val id: CollectiveDeviceId get() = configuration.deviceId

    val position = registerAsProperty(
        CollectiveDevice.position,
        position.debounce(configuration.reportInterval.milliseconds)
    )

    val velocity = registerAsProperty(
        CollectiveDevice.velocity,
        velocity.debounce(configuration.reportInterval.milliseconds)
    )

    private val _visibleNeighbors: MutableDeviceState<Collection<CollectiveDeviceId>> = stateOf(emptyList())

    val visibleNeighbors = registerAsProperty(
        CollectiveDevice.visibleNeighbors,
        DeviceState.map(_visibleNeighbors){ it.toList() }
    )

    init {
        position.onNext {
            _visibleNeighbors.value = observation.invoke().keys
        }
    }

    override suspend fun getPosition(): Gmc = position.value

    override suspend fun getVelocity(): GmcVelocity = velocity.value

    override suspend fun setVelocity(value: GmcVelocity) {
        velocity.value = value
    }

    override suspend fun listVisible(): Collection<CollectiveDeviceId> = observation.invoke().keys
}
