@file:OptIn(DFExperimental::class)

package space.kscience.controls.demo.collective

import space.kscience.controls.api.Device
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.registerAsProperty
import space.kscience.controls.spec.DeviceSpec
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.Scheme
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.maps.coordinates.Gmc
import kotlin.time.Duration.Companion.milliseconds

class CollectiveDeviceConfiguration(deviceId: DeviceId) : Scheme() {
    var deviceId by string(deviceId)
    var description by string()
}


interface CollectiveDevice : Device {

    public val id: DeviceId

    suspend fun getPosition(): Gmc

    suspend fun getVelocity(): GmcVelocity

    suspend fun setVelocity(value: GmcVelocity)

    suspend fun listVisible(): Collection<DeviceId>


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
    }
}


class CollectiveDeviceConstructor(
    context: Context,
    val configuration: CollectiveDeviceConfiguration,
    position: MutableDeviceState<Gmc>,
    velocity: MutableDeviceState<GmcVelocity>,
    private val listVisible: suspend () -> Collection<DeviceId>,
) : DeviceConstructor(context, configuration.meta), CollectiveDevice {

    override val id: DeviceId get() = configuration.deviceId

    val position = registerAsProperty(CollectiveDevice.position, position.sample(500.milliseconds))
    val velocity = registerAsProperty(CollectiveDevice.velocity, velocity)

    override suspend fun getPosition(): Gmc = position.value

    override suspend fun getVelocity(): GmcVelocity = velocity.value

    override suspend fun setVelocity(value: GmcVelocity) {
        velocity.value = value
    }

    override suspend fun listVisible(): Collection<DeviceId> = listVisible.invoke()
}