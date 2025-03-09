package space.kscience.controls.constructor.models

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context
import kotlin.math.pow
import kotlin.time.DurationUnit

/**
 * A model for inertial movement. Both linear and angular
 */
public class Inertia<U : UnitsOfMeasurement, V : UnitsOfMeasurement>(
    context: Context,
    force: DeviceState<Double>, //TODO add system unit sets
    inertia: Double,
    public val position: MutableDeviceState<NumericalValue<U>>,
    public val velocity: MutableDeviceState<NumericalValue<V>>,
) : ModelConstructor(context) {

    init {
        registerState(position)
        registerState(velocity)
    }

    private var currentForce = force.value

    private val movement = onTimer(DefaultTimer.REALTIME) { prev, next ->
        val dtSeconds = (next - prev).toDouble(DurationUnit.SECONDS)

        // compute new value based on velocity and acceleration from the previous step
        position.value += NumericalValue(velocity.value.value * dtSeconds + currentForce / inertia * dtSeconds.pow(2) / 2)

        // compute new velocity based on acceleration on the previous step
        velocity.value += NumericalValue(currentForce / inertia * dtSeconds)
        currentForce = force.value
    }

    public companion object {
        /**
         * Linear inertial model with [force] in newtons and [mass] in kilograms
         */
        public fun linear(
            context: Context,
            force: DeviceState<NumericalValue<Newtons>>,
            mass: NumericalValue<Kilograms>,
            position: MutableDeviceState<NumericalValue<Meters>>,
            velocity: MutableDeviceState<NumericalValue<MetersPerSecond>> = MutableDeviceState(NumericalValue(0.0)),
        ): Inertia<Meters, MetersPerSecond> = Inertia(
            context = context,
            force = force.values(),
            inertia = mass.value,
            position = position,
            velocity = velocity
        )

        public fun circular(
            context: Context,
            force: DeviceState<NumericalValue<NewtonsMeters>>,
            momentOfInertia: NumericalValue<KgM2>,
            position: MutableDeviceState<NumericalValue<Degrees>>,
            velocity: MutableDeviceState<NumericalValue<DegreesPerSecond>> = MutableDeviceState(NumericalValue(0.0)),
        ): Inertia<Degrees, DegreesPerSecond> = Inertia(
            context = context,
            force = force.values(),
            inertia = momentOfInertia.value,
            position = position,
            velocity = velocity
        )
    }
}