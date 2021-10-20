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
    private var speedState: Pair<Double, Double> = Pair(0.0, 0.0)
    get() {
        val previous_speed = field
        val current_acceleration = this.accelerationState
        val now = Instant.now().toEpochMilli().toDouble()
        val time_difference = now - this.timeState
        this.timeState = now
        field = Pair(
            previous_speed.first + time_difference * current_acceleration.first,
            previous_speed.second + time_difference * current_acceleration.second
        )
        return field
    }

    private var accelerationState: Pair<Double, Double> = Pair(0.0, 0.0)
    set(value) {
        this.speedState
        field = value
    }

    private var timeState = Instant.now().toEpochMilli().toDouble()

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
            doRecurring(Duration.milliseconds(50)){
                carProperties.read()
            }
        }
    }
}
