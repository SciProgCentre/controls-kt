package space.kscience.controls.demo

import kotlinx.coroutines.launch
import space.kscience.controls.api.metaDescriptor
import space.kscience.controls.spec.*
import space.kscience.controls.unit
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.value
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

class DemoDeviceState(
    var timeScale: Double = 5000.0,
    var sinScale: Double = 1.0,
    var cosScale: Double = 1.0,
    var comment: String = "",
) {
    fun time(): Instant = Instant.now()

    fun sinValue(): Double = sin(time().toEpochMilli().toDouble() / timeScale) * sinScale

    fun cosValue(): Double = cos(time().toEpochMilli().toDouble() / timeScale) * cosScale
}

object DemoDevice : DeviceFactory<DemoDeviceState>() {

    val timeScale by mutableDoubleProperty(
        descriptorBuilder = {
            description = "Real to virtual time scale"
        },
        read = { timeScale },
        write = { timeScale = it },
    )

    val sinScale by mutableDoubleProperty(
        descriptorBuilder = {
            description = "The scale of sin plot"
        },
        read = { sinScale },
        write = { sinScale = it },
    )

    val cosScale by mutableDoubleProperty(
        read = { cosScale },
        write = { cosScale = it },
    )

    val sin by doubleProperty { sinValue() }
    val cos by doubleProperty { cosValue() }

    val coordinates by metaProperty(
        descriptorBuilder = {
            metaDescriptor {
                value("time", ValueType.NUMBER)
            }
        }
    ) {
        Meta {
            "time" put time().toEpochMilli()
            "x" put read(DemoDevice.sin)
            "y" put read(DemoDevice.cos)
        }
    }

    val comment by mutableStringProperty(
        read = { comment },
        write = { comment = it }
    )

    val resetScale by action(MetaConverter.unit, MetaConverter.unit) {
        write(DemoDevice.timeScale, 5000.0)
        write(DemoDevice.sinScale, 1.0)
        write(DemoDevice.cosScale, 1.0)
    }

    val setSinScale by action(MetaConverter.double, MetaConverter.unit) { value: Double ->
        write(DemoDevice.sinScale, value)
    }

    context(base: DeviceBase)
    override suspend fun createState(): DemoDeviceState = DemoDeviceState().also {
        base.launch {
            base.read(sinScale)
            base.read(cosScale)
            base.read(timeScale)
        }
        base.doRecurring(50.milliseconds) {
            base.read(sin)
            base.read(cos)
            base.read(coordinates)
        }
    }
}