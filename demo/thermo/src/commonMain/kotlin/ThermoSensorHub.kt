package center.sciprog.controls.demo.thermo

import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceHub
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName


interface ThermoSensorHub : DeviceHub, ContextAware {
    val sensors: Map<String, ThermoSensorAnalyzer>
    val groups: Map<String, ThermoSensorGroupAnalyzer>

    override val devices: Map<Name, Device>
        get() = sensors.mapKeys { it.key.parseAsName() } + groups.mapKeys { NameToken("group",it.key).asName() }
}