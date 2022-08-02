package space.kscience.controls.demo

import kotlinx.coroutines.launch
import space.kscience.controls.api.metaDescriptor
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.value
import space.kscience.dataforge.meta.transformations.MetaConverter
import space.kscience.dataforge.values.ValueType
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime


class DemoDevice(context: Context, meta: Meta) : DeviceBySpec<DemoDevice>(DemoDevice, context, meta) {
    private var timeScaleState = 5000.0
    private var sinScaleState = 1.0
    private var cosScaleState = 1.0

    @OptIn(ExperimentalTime::class)
    override suspend fun open() {
        super.open()
        launch {
            sinScale.read()
            cosScale.read()
            timeScale.read()
        }
        doRecurring(50.milliseconds) {
            coordinates.read()
        }
    }


    companion object : DeviceSpec<DemoDevice>(), Factory<DemoDevice> {

        override fun build(context: Context, meta: Meta): DemoDevice = DemoDevice(context, meta)

        // register virtual properties based on actual object state
        val timeScale by mutableProperty(MetaConverter.double, DemoDevice::timeScaleState) {
            metaDescriptor {
                type(ValueType.NUMBER)
            }
            info = "Real to virtual time scale"
        }

        val sinScale by mutableProperty(MetaConverter.double, DemoDevice::sinScaleState)
        val cosScale by mutableProperty(MetaConverter.double, DemoDevice::cosScaleState)

        val sin by doubleProperty {
            val time = Instant.now()
            kotlin.math.sin(time.toEpochMilli().toDouble() / timeScaleState) * sinScaleState
        }

        val cos by doubleProperty {
            val time = Instant.now()
            kotlin.math.cos(time.toEpochMilli().toDouble() / timeScaleState) * sinScaleState
        }

        val coordinates by metaProperty(
            descriptorBuilder = {
                metaDescriptor {
                    value("time", ValueType.NUMBER)
                }
            }
        ) {
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

    }
}