package ru.mipt.npm.controls.demo

import ru.mipt.npm.controls.properties.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.ExperimentalTime


class DemoDevice : DeviceBySpec<DemoDevice>(DemoDevice) {
    private var timeScaleState = 5000.0
    private var sinScaleState = 1.0
    private var cosScaleState = 1.0

    companion object : DeviceSpec<DemoDevice>(::DemoDevice) {
        // register virtual properties based on actual object state
        val timeScale = registerProperty(MetaConverter.double, DemoDevice::timeScaleState)
        val sinScale = registerProperty(MetaConverter.double, DemoDevice::sinScaleState)
        val cosScale = registerProperty(MetaConverter.double, DemoDevice::cosScaleState)

        val sin by doubleProperty {
            val time = Instant.now()
            kotlin.math.sin(time.toEpochMilli().toDouble() / timeScaleState) * sinScaleState
        }

        val cos by doubleProperty {
            val time = Instant.now()
            kotlin.math.cos(time.toEpochMilli().toDouble() / timeScaleState) * sinScaleState
        }

        val coordinates by metaProperty {
            Meta {
                val time = Instant.now()
                "time" put time.toEpochMilli()
                "x" put read(sin)
                "y" put read(cos)
            }
        }

        val resetScale by action(MetaConverter.meta, MetaConverter.meta) {
            timeScale.write(5000.0)
            sinScale.write(1.0)
            cosScale.write(1.0)
            null
        }

        @OptIn(ExperimentalTime::class)
        override fun DemoDevice.onStartup() {
            doRecurring(Duration.milliseconds(50)){
                sin.read()
                cos.read()
            }
        }
    }
}