package ru.mipt.npm.controls.demo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import ru.mipt.npm.controls.base.*
import ru.mipt.npm.controls.controllers.DeviceSpec
import ru.mipt.npm.controls.controllers.double
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.values.asValue
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class DemoDevice(context: Context) : DeviceBase(context) {

    private val executor = Executors.newSingleThreadExecutor()

    override val scope: CoroutineScope = CoroutineScope(
        context.coroutineContext + executor.asCoroutineDispatcher() + Job(context.coroutineContext[Job])
    )

    val timeScale: DeviceProperty by writingVirtual(5000.0.asValue())
    var timeScaleValue by timeScale.double()

    val sinScale by writingVirtual(1.0.asValue())
    var sinScaleValue by sinScale.double()
    val sin: TypedReadOnlyDeviceProperty<Number> by readingNumber {
        val time = Instant.now()
        sin(time.toEpochMilli().toDouble() / timeScaleValue) * sinScaleValue
    }

    val cosScale by writingVirtual(1.0.asValue())
    var cosScaleValue by cosScale.double()
    val cos by readingNumber {
        val time = Instant.now()
        cos(time.toEpochMilli().toDouble() / timeScaleValue) * cosScaleValue
    }

    val coordinates by readingMeta {
        val time = Instant.now()
        "time" put time.toEpochMilli()
        "x" put sin(time.toEpochMilli().toDouble() / timeScaleValue) * sinScaleValue
        "y" put cos(time.toEpochMilli().toDouble() / timeScaleValue) * cosScaleValue
    }


    val resetScale: DeviceAction by acting {
        timeScaleValue = 5000.0
        sinScaleValue = 1.0
        cosScaleValue = 1.0
    }

    init {
        sin.readEvery(Duration.seconds(0.2))
        cos.readEvery(Duration.seconds(0.2))
        coordinates.readEvery(Duration.seconds(0.3))
    }

    override fun close() {
        super.close()
        executor.shutdown()
    }

    companion object : DeviceSpec<DemoDevice> {
        override fun invoke(meta: Meta, context: Context): DemoDevice = DemoDevice(context)
    }
}