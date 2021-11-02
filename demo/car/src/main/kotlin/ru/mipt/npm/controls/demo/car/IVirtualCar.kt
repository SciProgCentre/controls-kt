package ru.mipt.npm.controls.demo.car

import ru.mipt.npm.controls.api.Device
import ru.mipt.npm.controls.spec.DeviceSpec

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