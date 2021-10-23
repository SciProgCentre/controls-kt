package ru.mipt.npm.controls.demo.virtual_car

import kotlinx.coroutines.launch
import ru.mipt.npm.controls.properties.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.transformations.MetaConverter
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

data class Coordinates(val x: Double = 0.0, val y: Double = 0.0)

class VirtualCar : DeviceBySpec<VirtualCar>(VirtualCar) {
    private var speedState: Coordinates = Coordinates()
    private fun updateAndGetSpeed(): Coordinates {
        updateSpeedLocationTime()
        return this.speedState
    }

    private var locationState: Coordinates = Coordinates()
    private fun updateAndGetLocation(): Coordinates {
        updateSpeedLocationTime()
        return this.locationState
    }

    private var accelerationState: Coordinates = Coordinates()
    set(value) {
        updateSpeedLocationTime()
        field = value
    }

    private var timeState = Instant.now().toEpochMilli().toDouble()

    private fun updateSpeedLocationTime() {
        val previousSpeed = this.speedState
        val previousLocation = this.locationState
        val currentAcceleration = this.accelerationState
        val now = Instant.now().toEpochMilli().toDouble()
        val timeDifference = now - this.timeState
        this.timeState = now
        this.speedState = Coordinates(
            previousSpeed.x + timeDifference * currentAcceleration.x * 1e-3,
            previousSpeed.y + timeDifference * currentAcceleration.y * 1e-3
        )
        val locationDifference = Coordinates(
            timeDifference * 1e-3 * (previousSpeed.x + currentAcceleration.x * timeDifference * 1e-3 / 2),
            timeDifference * 1e-3 * (previousSpeed.y + currentAcceleration.y * timeDifference * 1e-3 / 2)
        )
        this.locationState = Coordinates(
            previousLocation.x + locationDifference.x,
            previousLocation.y + locationDifference.y
        )
    }

    object CoordinatesMetaConverter : MetaConverter<Coordinates> {
        override fun metaToObject(meta: Meta): Coordinates = Coordinates(
            meta["x"].double ?: 0.0,
            meta["y"].double ?: 0.0
        )

        override fun objectToMeta(obj: Coordinates): Meta = Meta {
            "x" put obj.x
            "y" put obj.y
        }
    }

    companion object : DeviceSpec<VirtualCar>(::VirtualCar) {
        val speed by property(CoordinatesMetaConverter) { this.updateAndGetSpeed() }

        val location by property(CoordinatesMetaConverter) { this.updateAndGetLocation() }

        val acceleration by property(CoordinatesMetaConverter, VirtualCar::accelerationState)

        val carProperties by metaProperty {
            Meta {
                val time = Instant.now()
                "time" put time.toEpochMilli()
                "speed" put CoordinatesMetaConverter.objectToMeta(read(speed))
                "location" put CoordinatesMetaConverter.objectToMeta(read(location))
                "acceleration" put CoordinatesMetaConverter.objectToMeta(read(acceleration))
            }
        }

        @OptIn(ExperimentalTime::class)
        override fun VirtualCar.onStartup() {
            launch {
                speed.read()
                acceleration.read()
                location.read()
            }
            doRecurring(Duration.seconds(1)){
                carProperties.read()
            }
        }
    }
}
