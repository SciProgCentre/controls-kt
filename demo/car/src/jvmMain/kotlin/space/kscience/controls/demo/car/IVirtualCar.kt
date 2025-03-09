package space.kscience.controls.demo.car

import space.kscience.controls.api.Device
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.mutableProperty
import space.kscience.controls.spec.property

interface IVirtualCar : Device {
    var speedState: Vector2D
    var locationState: Vector2D
    var accelerationState: Vector2D

    companion object : DeviceSpec<IVirtualCar>() {
        /**
         * Read-only speed
         */
        val speed by property(Vector2D, IVirtualCar::speedState)

        /**
         * Read-only location
         */
        val location by property(Vector2D, IVirtualCar::locationState)

        /**
         * writable acceleration
         */
        val acceleration by mutableProperty(Vector2D, IVirtualCar::accelerationState)
    }
}