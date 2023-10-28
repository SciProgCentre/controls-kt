package space.kscience.controls.constructor

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import space.kscience.controls.api.Device
import space.kscience.controls.spec.DeviceBySpec
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.doubleProperty
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.transformations.MetaConverter
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
        public val force: DevicePropertySpec<Drive, Double> by Drive.property(
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
    position: Double,
) : Drive, DeviceBySpec<Drive>(Drive, context) {

    private val dt = meta["time.step"].double?.milliseconds ?: 5.milliseconds
    private val clock = Clock.System

    override var force: Double = 0.0

    override var position: Double = position
        private set

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
                position += velocity * dtSeconds + force/mass * dtSeconds.pow(2) / 2

                // compute new velocity based on acceleration on the previous step
                velocity += force/mass * dtSeconds
            }
        }
    }

    override fun onStop() {
        updateJob?.cancel()
    }
}

