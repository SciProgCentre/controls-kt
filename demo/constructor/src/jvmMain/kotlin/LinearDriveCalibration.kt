package space.kscience.controls.demo.constructor

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.device
import space.kscience.controls.constructor.useValue
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.models.mechanical.LimitSwitch
import space.kscience.controls.models.mechanical.MutableRangeState
import space.kscience.controls.models.mechanical.StepDrive
import space.kscience.dataforge.context.Context
import kotlin.time.Duration.Companion.seconds

private val ticksPerSecond = 3000.0

class LinearStepDrive(
    context: Context,
    drive: StepDrive,
    atStart: LimitSwitch,
    atEnd: LimitSwitch,
) : DeviceConstructor(context) {
    val drive by device(drive)
    val atStart by device(atStart)
    val atEnd by device(atEnd)
}


fun LinearStepDrive(
    context: Context,
    position: MutableRangeState<Long>,
): LinearStepDrive = LinearStepDrive(
    context = context,
    drive = StepDrive(context, ticksPerSecond, position),
    atStart = LimitSwitch(context, position.atStart),
    atEnd = LimitSwitch(context, position.atEnd)
)

suspend fun LinearStepDrive.calibrate(step: Long = 10): ClosedRange<Long> = coroutineScope {
    do {
        ensureActive()
        drive.target.value -= step
        delay((step / ticksPerSecond).seconds)
    } while (!atStart.locked.value)

    val start = drive.position.value


    do {
        ensureActive()
        drive.target.value += step
        delay((step / ticksPerSecond).seconds)
    } while (!atEnd.locked.value)

    val end = drive.position.value

    return@coroutineScope start..end
}

suspend fun main() = coroutineScope {
    val context = Context {
        plugin(DeviceManager)
    }

    val positionModel = MutableRangeState<Long>(0L, -1000L..1012L)

    val linearStepDrive = LinearStepDrive(context, positionModel)

    val printJob = linearStepDrive.drive.target.useValue(this){
        println("Move to $it")
    }

    println(linearStepDrive.calibrate())

    printJob.cancel()
}