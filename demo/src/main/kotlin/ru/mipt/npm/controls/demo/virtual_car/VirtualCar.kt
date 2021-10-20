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

    private var accelerationState: Pair<Double, Double> = Pair(0.0, 0.0)
    set(value) {
        updateSpeedLocationTime()
        field = value
    }

    private var timeState = Instant.now().toEpochMilli().toDouble()

    private fun updateSpeedLocationTime() {
        val previousSpeed = this._speedState
        val currentAcceleration = this.accelerationState
        val now = Instant.now().toEpochMilli().toDouble()
        val timeDifference = now - this.timeState
        this.timeState = now
        this._speedState = Pair(
            previousSpeed.first + timeDifference * currentAcceleration.first * 1e-3,
            previousSpeed.second + timeDifference * currentAcceleration.second * 1e-3
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

        val acceleration by property(DoublePairMetaConverter, VirtualCar::accelerationState)

        val carProperties by metaProperty {
            Meta {
                val time = Instant.now()
                "time" put time.toEpochMilli()
                "speed" put DoublePairMetaConverter.objectToMeta(read(speed))
                "acceleration" put DoublePairMetaConverter.objectToMeta(read(acceleration))
            }
        }

        @OptIn(ExperimentalTime::class)
        override fun VirtualCar.onStartup() {
            launch {
                speed.read()
                acceleration.read()
            }
            doRecurring(Duration.seconds(1)){
                carProperties.read()
            }
        }
    }
}
