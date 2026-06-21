package center.sciprog.controls.demo.thermo

import space.kscience.controls.spec.AbstractDeviceSpec
import space.kscience.dataforge.meta.MetaConverter

/**
 * A specification for a thermo sensor device.
 */
object ThermoSensorSpec : AbstractDeviceSpec() {
    val temperature by property(MetaConverter.double)
}