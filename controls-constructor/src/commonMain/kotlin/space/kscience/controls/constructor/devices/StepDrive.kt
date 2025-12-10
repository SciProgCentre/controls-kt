package space.kscience.controls.constructor.devices

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.Degrees
import space.kscience.controls.constructor.units.NumericAmount
import space.kscience.controls.constructor.units.plus
import space.kscience.controls.constructor.units.times
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * A step drive
 *
 * @param ticksPerSecond ticks per second
 * @param target target ticks state
 * @param writeTicks a hardware callback
 */
public class StepDrive(
    context: Context,
    ticksPerSecond: Double,
    position: MutableValueState<Long> = MutableDeviceState(0),
    private val writeTicks: suspend (ticks: Long, speed: Double) -> Unit = { _, _ -> },
) : DeviceConstructor(context) {

    public val target: MutableValueState<Long> by property(
        MetaConverter.long,
        MutableDeviceState<Long>(position.value)
    )

    public val speed: MutableValueState<Double> by property(
        MetaConverter.double,
        MutableDeviceState<Double>(ticksPerSecond)
    )

    public val position: ValueState<Long> by property(MetaConverter.long, position)

    //FIXME round to zero problem
    private val ticker = onTimer(20.milliseconds, reads = setOf(target, position), writes = setOf(position)) { prev, next ->
        val tickSpeed = speed.value
        val timeDelta = (next - prev).toDouble(DurationUnit.SECONDS)
        val ticksDelta: Long = target.value - position.value
        val steps: Long = when {
            ticksDelta > 0 -> min(ticksDelta, (timeDelta * tickSpeed).roundToLong())
            ticksDelta < 0 -> max(ticksDelta, -(timeDelta * tickSpeed).roundToLong())
            else -> return@onTimer
        }
        writeTicks(steps, tickSpeed)
        position.value += steps
    }
}

/**
 * Compute a state using given tick-to-angle transformation
 */
public fun StepDrive.angle(
    step: NumericAmount<Degrees>,
    zero: NumericAmount<Degrees> = NumericAmount(0),
): ValueState<NumericAmount<Degrees>> = position.map(this) {
    zero + it * step
}

