package ru.mipt.npm.controls.demo.virtual_car

import kotlinx.coroutines.launch
import ru.mipt.npm.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.transformations.MetaConverter
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

data class Coordinates(val x: Double = 0.0, val y: Double = 0.0)

class VirtualCar(context: Context, meta: Meta) : DeviceBySpec<VirtualCar>(VirtualCar, context, meta) {
    private var speedState: Coordinates = Coordinates()

    private var locationState: Coordinates = Coordinates()

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

    @OptIn(ExperimentalTime::class)
    override suspend fun open() {
        super.open()
        launch {
            doRecurring(Duration.seconds(1)) {
                carProperties.read()
            }
        }
        launch {
            doRecurring(Duration.milliseconds(50)) {
                updateSpeedLocationTime()
                updateLogical(speed, this@VirtualCar.speedState)
                updateLogical(acceleration, this@VirtualCar.accelerationState)
                updateLogical(location, this@VirtualCar.locationState)
            }
        }
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

    companion object : DeviceSpec<VirtualCar>(), Factory<VirtualCar> {
        override fun invoke(meta: Meta, context: Context): VirtualCar = VirtualCar(context, meta)

        val speed by property(CoordinatesMetaConverter) { this.speedState }

        val location by property(CoordinatesMetaConverter) { this.locationState }

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
    }
}
