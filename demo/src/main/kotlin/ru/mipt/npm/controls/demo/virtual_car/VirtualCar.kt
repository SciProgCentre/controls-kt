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

class VirtualCar : DeviceBySpec<VirtualCar>(VirtualCar) {
    private var _speedState: Pair<Double, Double> = Pair(0.0, 0.0)
    private val speedState: Pair<Double, Double>
    get() {
        updateSpeedLocationTime()
        return this._speedState
    }

    private var _locationState: Pair<Double, Double> = Pair(0.0, 0.0)
    private val locationState: Pair<Double, Double>
    get() {
        updateSpeedLocationTime()
        return this._locationState
    }

    private var accelerationState: Pair<Double, Double> = Pair(0.0, 0.0)
    set(value) {
        updateSpeedLocationTime()
        field = value
    }

    private var timeState = Instant.now().toEpochMilli().toDouble()

    private fun updateSpeedLocationTime() {
        val previousSpeed = this._speedState
        val previousLocation = this._locationState
        val currentAcceleration = this.accelerationState
        val now = Instant.now().toEpochMilli().toDouble()
        val timeDifference = now - this.timeState
        this.timeState = now
        this._speedState = Pair(
            previousSpeed.first + timeDifference * currentAcceleration.first * 1e-3,
            previousSpeed.second + timeDifference * currentAcceleration.second * 1e-3
        )
        val locationDifference = Pair(
            timeDifference * 1e-3 * (previousSpeed.first + currentAcceleration.first * timeDifference * 1e-3 / 2),
            timeDifference * 1e-3 * (previousSpeed.second + currentAcceleration.second * timeDifference * 1e-3 / 2)
        )
        this._locationState = Pair(
            previousLocation.first + locationDifference.first,
            previousLocation.second + locationDifference.second
        )
    }

    object DoublePairMetaConverter : MetaConverter<Pair<Double, Double>> {
        override fun metaToObject(meta: Meta): Pair<Double, Double> = Pair(
            meta["x"].double ?: 0.0,
            meta["y"].double ?: 0.0
        )

        override fun objectToMeta(obj: Pair<Double, Double>): Meta = Meta {
            "x" put obj.first
            "y" put obj.second
        }
    }

    companion object : DeviceSpec<VirtualCar>(::VirtualCar) {
        val speed by property(DoublePairMetaConverter) { this.speedState }

        val location by property(DoublePairMetaConverter) { this.locationState }

        val acceleration by property(DoublePairMetaConverter, VirtualCar::accelerationState)

        val carProperties by metaProperty {
            Meta {
                val time = Instant.now()
                "time" put time.toEpochMilli()
                "speed" put DoublePairMetaConverter.objectToMeta(read(speed))
                "location" put DoublePairMetaConverter.objectToMeta(read(location))
                "acceleration" put DoublePairMetaConverter.objectToMeta(read(acceleration))
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
