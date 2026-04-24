@file:OptIn(DFExperimental::class)

package space.kscience.controls.demo.collective

import space.kscience.controls.constructor.*
import space.kscience.controls.peer.PeerConnection
import space.kscience.controls.spec.AbstractDeviceSpec
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


class CollectiveDevice(
    context: Context,
    val configuration: CollectiveDeviceConfiguration,
    position: MutableValueState<Gmc>,
    velocity: MutableValueState<GmcVelocity>,
    val peerConnection: PeerConnection,
    private val observation: suspend () -> Map<CollectiveDeviceId, GmcCurve>,
) : DeviceConstructor(context, configuration.meta) {

    val id: CollectiveDeviceId get() = configuration.deviceId

    val position = registerAsProperty(
        CollectiveDevice.position,
        position.sample(configuration.reportInterval.milliseconds)
    )

    val velocity = registerAsProperty(
        CollectiveDevice.velocity,
        velocity.sample(configuration.reportInterval.milliseconds)
    )

    private val _visibleNeighbors: MutableValueState<Collection<CollectiveDeviceId>> = stateOf(emptyList())

    val visibleNeighbors = registerAsProperty(
        CollectiveDevice.visibleNeighbors,
        ValueState.map(_visibleNeighbors) { it.toList() }
    )

    init {
        position.onNext {
            _visibleNeighbors.value = observation.invoke().keys
        }
    }

    companion object : AbstractDeviceSpec() {
        val position by property<Gmc>(
            converter = MetaConverter.serializable()
        )

        val velocity by mutableProperty<GmcVelocity>(
            converter = MetaConverter.serializable(),
        )

        val visibleNeighbors by property(
            MetaConverter.stringList,
        )
    }
}
