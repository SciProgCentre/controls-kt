package hep.dataforge.control.demo

import hep.dataforge.control.base.*
import hep.dataforge.control.controllers.double
import hep.dataforge.values.asValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@OptIn(ExperimentalTime::class)
class DemoDevice(parentScope: CoroutineScope = GlobalScope) : DeviceBase() {

    private val executor = Executors.newSingleThreadExecutor()

    override val scope: CoroutineScope = CoroutineScope(
        parentScope.coroutineContext + executor.asCoroutineDispatcher()
    )

    val timeScale: IsolatedDeviceProperty by writingVirtual(5000.0.asValue())
    var timeScaleValue by timeScale.double()

    val sinScale by writingVirtual(1.0.asValue())
    var sinScaleValue by sinScale.double()
    val sin by readingNumber {
        val time = Instant.now()
        sin(time.toEpochMilli().toDouble() / timeScaleValue)*sinScaleValue
    }

    val cosScale by writingVirtual(1.0.asValue())
    var cosScaleValue by cosScale.double()
    val cos by readingNumber {
        val time = Instant.now()
        cos(time.toEpochMilli().toDouble() / timeScaleValue)*cosScaleValue
    }

    val coordinates by readingMeta {
        val time = Instant.now()
        "time" put time.toEpochMilli()
        "x" put sin(time.toEpochMilli().toDouble() / timeScaleValue)*sinScaleValue
        "y" put cos(time.toEpochMilli().toDouble() / timeScaleValue)*cosScaleValue
    }


    val resetScale: Action by action {
        timeScaleValue = 5000.0
        sinScaleValue = 1.0
        cosScaleValue = 1.0
    }

    init {
        sin.readEvery(0.2.seconds)
        cos.readEvery(0.2.seconds)
        coordinates.readEvery(0.3.seconds)
    }

    override fun close() {
        super.close()
        executor.shutdown()
    }
}