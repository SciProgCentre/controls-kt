@file:OptIn(DFExperimental::class)

package space.kscience.controls.demo.map

import space.kscience.controls.api.Device
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.registerAsProperty
import space.kscience.controls.spec.DeviceSpec
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.Scheme
import space.kscience.dataforge.meta.SchemeSpec
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.maps.coordinates.Gmc
import kotlin.time.Duration.Companion.milliseconds

class RemoteDeviceConfiguration : Scheme() {
    companion object : SchemeSpec<RemoteDeviceConfiguration>(::RemoteDeviceConfiguration)
}


interface RemoteDevice : Device {

    suspend fun getPosition(): Gmc

    suspend fun getVelocity(): GmcVelocity

    suspend fun setVelocity(value: GmcVelocity)


    companion object : DeviceSpec<RemoteDevice>() {
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


class RemoteDeviceConstructor(
    context: Context,
    val configuration: RemoteDeviceConfiguration,
    position: MutableDeviceState<Gmc>,
    velocity: MutableDeviceState<GmcVelocity>,
) : DeviceConstructor(context, configuration.meta), RemoteDevice {

    val position = registerAsProperty(RemoteDevice.position, position.debounce(500.milliseconds))
    val velocity = registerAsProperty(RemoteDevice.velocity, velocity)

    override suspend fun getPosition(): Gmc = position.value

    override suspend fun getVelocity(): GmcVelocity = velocity.value

    override suspend fun setVelocity(value: GmcVelocity) {
        velocity.value = value
    }
}