package space.kscience.controls.constructor.models

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.asName
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * A model for inertial movement. Both linear and angular
 */
public class Inertia<U : UnitsOfMeasurement, V : UnitsOfMeasurement>(
    context: Context,
    force: ValueState<Double>, //TODO add system unit sets
    inertia: Double,
    public val position: MutableValueState<NumericAmount<U>>,
    public val velocity: MutableValueState<NumericAmount<V>>,
) : DeviceConstructor(context) {

    init {
        registerState(position, "position".asName())
        registerState(velocity, "velocity".asName())
    }

    private var currentForce = force.value

    private val movement = onTimer(5.milliseconds) { prev, next ->
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
            force: ValueState<NumericAmount<Newtons>>,
            mass: NumericAmount<Kilograms>,
            position: MutableValueState<NumericAmount<Meters>>,
            velocity: MutableValueState<NumericAmount<MetersPerSecond>> = MutableValueState(
                NumericAmount(0.0),
                context.clock
            ),
        ): Inertia<Meters, MetersPerSecond> = Inertia(
            context = context,
            force = force.values(),
            inertia = mass.value,
            position = position,
            velocity = velocity
        )

        public fun circular(
            context: Context,
            force: ValueState<NumericAmount<NewtonsMeters>>,
            momentOfInertia: NumericAmount<KgM2>,
            position: MutableValueState<NumericAmount<Degrees>>,
            velocity: MutableValueState<NumericAmount<DegreesPerSecond>> = MutableValueState(
                NumericAmount(0.0),
                context.clock
            ),
        ): Inertia<Degrees, DegreesPerSecond> = Inertia(
            context = context,
            force = force.values(),
            inertia = momentOfInertia.value,
            position = position,
            velocity = velocity
        )
    }
}