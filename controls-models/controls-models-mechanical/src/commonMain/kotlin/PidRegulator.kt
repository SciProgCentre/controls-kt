package space.kscience.controls.models.mechanical

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.Name
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
    private val position: ValueState<NumericAmount<P>>,
    public var pidParameters: PidParameters, // TODO expose as property
    output: MutableValueState<NumericAmount<O>> = MutableValueState(NumericAmount(0.0)),
    private val convertOutput: (NumericAmount<P>) -> NumericAmount<O> = { NumericAmount(it.value) },
) : DeviceConstructor(context) {

    public val target: MutableValueState<NumericAmount<P>> = stateOf(NumericAmount(0.0), Name.of("target"))
    public val output: MutableValueState<NumericAmount<O>> = registerState(output, Name.of("output"))

    private val updateJob = launch {
        var lastPosition: NumericAmount<P> = target.value

        var integral: NumericAmount<P> = NumericAmount(0.0)

        val mutex = Mutex()

        var lastTime = clock.now()

        while (isActive) {
            delay(pidParameters.timeStep)
            mutex.withLock {
                val realTime = clock.now()
                val delta: NumericAmount<P> = target.value - position.value
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