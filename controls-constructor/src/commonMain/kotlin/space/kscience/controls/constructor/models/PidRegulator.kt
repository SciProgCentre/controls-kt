package space.kscience.controls.constructor.models

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.controls.manager.clock
import space.kscience.dataforge.context.Context
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit


/**
 * Pid regulator parameters
 */
public data class PidParameters(
    val kp: Double,
    val ki: Double,
    val kd: Double,
    val timeStep: Duration = 10.milliseconds,
)

/**
 * A PID regulator
 *
 * @param P units of position values
 * @param O units of output values
 */
public class PidRegulator<P : UnitsOfMeasurement, O : UnitsOfMeasurement>(
    context: Context,
    private val position: DeviceState<NumericalValue<P>>,
    public var pidParameters: PidParameters, // TODO expose as property
    output: MutableDeviceState<NumericalValue<O>> = MutableDeviceState(NumericalValue(0.0)),
    private val convertOutput: (NumericalValue<P>) -> NumericalValue<O> = { NumericalValue(it.value) },
) : ModelConstructor(context) {

    public val target: MutableDeviceState<NumericalValue<P>> = stateOf(NumericalValue(0.0))
    public val output: MutableDeviceState<NumericalValue<O>> = registerState(output)

    private val updateJob = launch {
        var lastPosition: NumericalValue<P> = target.value

        var integral: NumericalValue<P> = NumericalValue(0.0)

        val mutex = Mutex()

        val clock = context.clock

        var lastTime = clock.now()

        while (isActive) {
            delay(pidParameters.timeStep)
            mutex.withLock {
                val realTime = clock.now()
                val delta: NumericalValue<P> = target.value - position.value
                val dtSeconds = (realTime - lastTime).toDouble(DurationUnit.SECONDS)
                integral += delta * dtSeconds
                val derivative = (position.value - lastPosition) / dtSeconds

                //set last time and value to new values
                lastTime = realTime
                lastPosition = position.value

                output.value =
                    convertOutput(pidParameters.kp * delta + pidParameters.ki * integral + pidParameters.kd * derivative)
            }
        }
    }
}