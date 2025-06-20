package center.sciprog.controls.demo.thermo

import space.kscience.controls.api.Device
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.doRecurring
import space.kscience.controls.spec.doubleProperty
import space.kscience.controls.spec.read
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import kotlin.time.Duration.Companion.seconds

interface ThermoSensor : Device {

    suspend fun readTemperature(): Double

    companion object : DeviceSpec<ThermoSensor>() {
        val temperature by doubleProperty { readTemperature() }

        override suspend fun ThermoSensor.onOpen() {
            val readInterval = meta["readInterval"].double ?: 2.0
            doRecurring(readInterval.seconds) {
                read(temperature)
            }
        }

    }
}