package ru.mipt.npm.controls.demo

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.mipt.npm.controls.properties.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter
import java.time.Instant


class DemoDevice : DeviceBySpec<DemoDevice>(DemoDevice) {
    var timeScale by state(5000.0)
    var sinScale by state( 1.0)
    var cosScale by state(1.0)

    companion object : DeviceSpec<DemoDevice>(::DemoDevice) {
        // register virtual properties based on actual object state
        val timeScaleProperty = registerProperty(MetaConverter.double, DemoDevice::timeScale)
        val sinScaleProperty = registerProperty(MetaConverter.double, DemoDevice::sinScale)
        val cosScaleProperty = registerProperty(MetaConverter.double, DemoDevice::cosScale)

        val sin by doubleProperty {
            val time = Instant.now()
            kotlin.math.sin(time.toEpochMilli().toDouble() / timeScale) * sinScale
        }

        val cos by doubleProperty {
            val time = Instant.now()
            kotlin.math.cos(time.toEpochMilli().toDouble() / timeScale) * sinScale
        }

        val coordinates by metaProperty {
            Meta {
                val time = Instant.now()
                "time" put time.toEpochMilli()
                "x" put getSuspend(sin)
                "y" put getSuspend(cos)
            }
        }

        val resetScale by action(MetaConverter.meta, MetaConverter.meta) {
            timeScale = 5000.0
            sinScale = 1.0
            cosScale = 1.0
            null
        }

        override fun DemoDevice.onStartup() {
            launch {
                while(isActive){
                    delay(50)
                    sin.read()
                    cos.read()
                }
            }
        }
    }
}