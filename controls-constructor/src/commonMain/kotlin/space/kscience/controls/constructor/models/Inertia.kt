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
    public val position: MutableDeviceState<NumericAmount<U>>,
    public val velocity: MutableDeviceState<NumericAmount<V>>,
) : ModelConstructor(context) {

    init {
        registerState(position)
        registerState(velocity)
    }

    private var currentForce = force.value

    private val movement = onTimer(DefaultTimer.REALTIME) { prev, next ->
        val dtSeconds = (next - prev).toDouble(DurationUnit.SECONDS)

        // compute new value based on velocity and acceleration from the previous step
        position.value += NumericAmount(velocity.value.value * dtSeconds + currentForce / inertia * dtSeconds.pow(2) / 2)

        // compute new velocity based on acceleration on the previous step
        velocity.value += NumericAmount(currentForce / inertia * dtSeconds)
        currentForce = force.value
    }

    public companion object {
        /**
         * Linear inertial model with [force] in newtons and [mass] in kilograms
         */
        public fun linear(
            context: Context,
            force: DeviceState<NumericAmount<Newtons>>,
            mass: NumericAmount<Kilograms>,
            position: MutableDeviceState<NumericAmount<Meters>>,
            velocity: MutableDeviceState<NumericAmount<MetersPerSecond>> = MutableDeviceState(NumericAmount(0.0)),
        ): Inertia<Meters, MetersPerSecond> = Inertia(
            context = context,
            force = force.values(),
            inertia = mass.value,
            position = position,
            velocity = velocity
        )

        public fun circular(
            context: Context,
            force: DeviceState<NumericAmount<NewtonsMeters>>,
            momentOfInertia: NumericAmount<KgM2>,
            position: MutableDeviceState<NumericAmount<Degrees>>,
            velocity: MutableDeviceState<NumericAmount<DegreesPerSecond>> = MutableDeviceState(NumericAmount(0.0)),
        ): Inertia<Degrees, DegreesPerSecond> = Inertia(
            context = context,
            force = force.values(),
            inertia = momentOfInertia.value,
            position = position,
            velocity = velocity
        )
    }
}