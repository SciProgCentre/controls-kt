package center.sciprog.controls.devices.misc

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.kscience.controls.api.Device
import space.kscience.controls.spec.DeviceBySpec
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.doubleProperty
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit


interface PidRegulator : Device {
    /**
     * Proportional coefficient
     */
    val kp: Double

    /**
     * Integral coefficient
     */
    val ki: Double

    /**
     * Differential coefficient
     */
    val kd: Double

    /**
     * The target value for PID
     */
    var target: Double

    /**
     * Read current value
     */
    suspend fun read(): Double

    companion object : DeviceSpec<PidRegulator>() {
        val target by property(MetaConverter.double, PidRegulator::target)
        val value by doubleProperty { read() }
    }
}

/**
 *
 */
class VirtualPid(
    context: Context,
    override val kp: Double,
    override val ki: Double,
    override val kd: Double,
    val mass: Double,
    override var target: Double = 0.0,
    private val dt: Duration = 0.5.milliseconds,
    private val clock: Clock = Clock.System,
) : DeviceBySpec<PidRegulator>(PidRegulator, context), PidRegulator {

    private val mutex = Mutex()


    private var lastTime: Instant = clock.now()
    private var lastValue: Double = target

    private var value: Double = target
    private var velocity: Double = 0.0
    private var acceleration: Double = 0.0
    private var integral: Double = 0.0


    private var updateJob: Job? = null

    override suspend fun onStart() {
        updateJob = launch {
            while (isActive) {
                delay(dt)
                mutex.withLock {
                    val realTime = clock.now()
                    val delta = target - value
                    val dtSeconds = (realTime - lastTime).toDouble(DurationUnit.SECONDS)
                    integral += delta * dtSeconds
                    val derivative = (value - lastValue) / dtSeconds

                    //set last time and value to new values
                    lastTime = realTime
                    lastValue = value

                    // compute new value based on velocity and acceleration from the previous step
                    value += velocity * dtSeconds + acceleration * dtSeconds.pow(2) / 2

                    // compute new velocity based on acceleration on the previous step
                    velocity += acceleration * dtSeconds

                    //compute force for the next step based on current values
                    acceleration = (kp * delta + ki * integral + kd * derivative) / mass


                    check(value.isFinite() && velocity.isFinite()) {
                        "Value $value is not finite"
                    }
                }
            }
        }
    }

    override fun onStop() {
        updateJob?.cancel()
        super<PidRegulator>.stop()
    }

    override suspend fun read(): Double = value

    suspend fun readVelocity(): Double = velocity

    suspend fun readAcceleration(): Double = acceleration

    suspend fun write(newTarget: Double) = mutex.withLock {
        require(newTarget.isFinite()) { "Value $newTarget is not valid" }
        target = newTarget
    }

    companion object : Factory<Device> {
        override fun build(context: Context, meta: Meta) = VirtualPid(
            context,
            meta["kp"].double ?: error("Kp is not defined"),
            meta["ki"].double ?: error("Ki is not defined"),
            meta["kd"].double ?: error("Kd is not defined"),
            meta["m"].double ?: error("Mass is not defined"),
        )

    }
}