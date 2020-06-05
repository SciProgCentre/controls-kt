package hep.dataforge.control.demo

import hep.dataforge.control.base.*
import hep.dataforge.control.controlers.double
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

    val scaleProperty: SimpleDeviceProperty by writingVirtual(5000.0.asValue())
    var scale by scaleProperty.double()

    val resetScale: Action by action {
        scale = 5000.0
    }

    val sin by readingNumber {
        val time = Instant.now()
        sin(time.toEpochMilli().toDouble() / scale)
    }

    val cos by readingNumber {
        val time = Instant.now()
        cos(time.toEpochMilli().toDouble() / scale)
    }

    val coordinates by readingMeta {
        val time = Instant.now()
        "time" put time.toEpochMilli()
        "x" put sin(time.toEpochMilli().toDouble() / scale)
        "y" put cos(time.toEpochMilli().toDouble() / scale)
    }

    init {
        sin.readEvery(0.2.seconds)
        cos.readEvery(0.2.seconds)
        coordinates.readEvery(0.2.seconds)
    }

    override fun close() {
        super.close()
        executor.shutdown()
    }
}