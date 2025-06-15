package center.sciprog.controls.demo.thermo

import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceHub
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName


interface ThermoSensorHub : DeviceHub, ContextAware {
    val sensors: Map<String, ThermoSensorAnalyzer>

    override val devices: Map<Name, Device> get() = sensors.mapKeys { it.key.parseAsName() }
}