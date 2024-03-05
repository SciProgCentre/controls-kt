package space.kscience.controls.constructor

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.kscience.controls.api.Device
import space.kscience.controls.manager.clock
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * A classic drive regulated by force with encoder
 */
public interface Drive : Device {
    /**
     * Get or set drive force or momentum
     */
    public var force: Double

    /**
     * Current position value
     */
    public val position: Double

    public companion object : DeviceSpec<Drive>() {
        public val force: MutableDevicePropertySpec<Drive, Double> by Drive.mutableProperty(
            MetaConverter.double,
            Drive::force
        )

        public val position: DevicePropertySpec<Drive, Double> by doubleProperty { position }
    }
}

/**
 * A virtual drive
 */
public class VirtualDrive(
    context: Context,
    private val mass: Double,
    public val positionState: MutableDeviceState<Double>,
) : Drive, DeviceBySpec<Drive>(Drive, context) {

    private val dt = meta["time.step"].double?.milliseconds ?: 1.milliseconds
    private val clock = context.clock

    override var force: Double = 0.0

    override val position: Double get() = positionState.value

    public var velocity: Double = 0.0
        private set

    private var updateJob: Job? = null

    override suspend fun onStart() {
        updateJob = launch {
            var lastTime = clock.now()
            while (isActive) {
                delay(dt)
                val realTime = clock.now()
                val dtSeconds = (realTime - lastTime).toDouble(DurationUnit.SECONDS)

                //set last time and value to new values
                lastTime = realTime

                // compute new value based on velocity and acceleration from the previous step
                positionState.value += velocity * dtSeconds + force / mass * dtSeconds.pow(2) / 2
                propertyChanged(Drive.position, positionState.value)

                // compute new velocity based on acceleration on the previous step
                velocity += force / mass * dtSeconds
            }
        }
    }

    override fun onStop() {
        updateJob?.cancel()
    }

    public companion object {
        public fun factory(
            mass: Double,
            positionState: MutableDeviceState<Double>,
        ): Factory<Drive> = Factory { context, _ ->
            VirtualDrive(context, mass, positionState)
        }
    }
}

public suspend fun Drive.stateOfForce(): MutableDeviceState<Double> = mutablePropertyAsState(Drive.force)
