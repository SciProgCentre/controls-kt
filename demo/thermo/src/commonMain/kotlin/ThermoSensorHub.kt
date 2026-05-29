package center.sciprog.controls.demo.thermo

import space.kscience.controls.api.DeviceTree
import space.kscience.dataforge.context.ContextAware


interface ThermoSensorHub : DeviceTree, ContextAware {
    val sensors: Map<String, ThermoSensorAnalyzer>
    val groups: Map<String, ThermoSensorGroupAnalyzer>

    override val children: Map<String, DeviceTree> get() = sensors + groups.mapKeys { "group[${it.key}]" }
}