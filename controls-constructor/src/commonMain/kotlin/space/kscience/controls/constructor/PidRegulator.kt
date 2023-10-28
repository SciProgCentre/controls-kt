package space.kscience.controls.constructor

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.kscience.controls.spec.DeviceBySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * A drive with PID regulator
 */
public class PidRegulator(
    public val drive: Drive,
    public val kp: Double,
    public val ki: Double,
    public val kd: Double,
    private val dt: Duration = 1.milliseconds,
    private val clock: Clock = Clock.System,
) : DeviceBySpec<Regulator>(Regulator, drive.context), Regulator {

    override var target: Double = drive.position

    private var lastTime: Instant = clock.now()
    private var lastPosition: Double = target

    private var integral: Double = 0.0


    private var updateJob: Job? = null
    private val mutex = Mutex()


    override suspend fun onStart() {
        drive.start()
        updateJob = launch {
            while (isActive) {
                delay(dt)
                mutex.withLock {
                    val realTime = clock.now()
                    val delta = target - position
                    val dtSeconds = (realTime - lastTime).toDouble(DurationUnit.SECONDS)
                    integral += delta * dtSeconds
                    val derivative = (drive.position - lastPosition) / dtSeconds

                    //set last time and value to new values
                    lastTime = realTime
                    lastPosition = drive.position

                    drive.force = kp * delta + ki * integral + kd * derivative
                }
            }
        }
    }

    override fun onStop() {
        updateJob?.cancel()
    }

    override val position: Double get() = drive.position

}