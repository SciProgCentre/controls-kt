package center.sciprog.controls.demo.thermo

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.Context

class ModbusThermoSensorHub(
    val deviceManager: DeviceManager,
    val master: AbstractModbusMaster,
    val configuration: Map<String, ThermoSensorConfig>
) : ThermoSensorHub {

    override val context: Context get() = deviceManager.context

    override val sensors: Map<String, ThermoSensorAnalyzer> = configuration.mapValues { (name, sensorConfig) ->
        ThermoSensorAnalyzer(
            sensor = ModbusThermoSensor(
                context = context,
                master = master,
                unitId = sensorConfig.unitId,
                address = sensorConfig.address,
                meta = sensorConfig.meta
            ),
            description = sensorConfig
        ).also { sensor ->
            deviceManager.install(name, sensor)
        }
    }
}