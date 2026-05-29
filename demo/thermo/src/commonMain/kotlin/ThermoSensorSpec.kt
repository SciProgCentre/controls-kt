package center.sciprog.controls.demo.thermo

import space.kscience.controls.spec.AbstractDeviceSpec
import space.kscience.dataforge.meta.MetaConverter


object ThermoSensorSpec : AbstractDeviceSpec() {
    val temperature by property(MetaConverter.double)
}

/*
        override suspend fun ThermoSensor.onOpen() {
            val readInterval = meta["readInterval"].double ?: 2.0
            doRecurring(readInterval.seconds) {
                read(temperature)
            }
        }
 */