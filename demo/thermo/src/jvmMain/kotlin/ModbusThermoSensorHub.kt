package center.sciprog.controls.demo.thermo

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.names.asName

class ModbusThermoSensorHub(
    val deviceManager: DeviceManager,
    val configuration: ThermoSensorHubConfig
) : ThermoSensorHub, AutoCloseable {

    override val context: Context get() = deviceManager.context

    private val masterCache = mutableMapOf<ThermoSensorModbusConfig, AbstractModbusMaster>()

    //TODO move out of class
    override val sensors: Map<String, ThermoSensorAnalyzer> = configuration.sensors.mapValues { (name, sensorConfig) ->
        val modbusConfig = ThermoSensorModbusConfig.combine(sensorConfig.modbus, configuration.modbusDefault)

        val master = masterCache.getOrPut(modbusConfig) {
            ModbusTCPMaster(
                /* addr = */ modbusConfig.host ?: "localhost",
                /* port = */ modbusConfig.port ?: 502
            ).also {
                it.connect()
            }
        }

        ThermoSensorAnalyzer(
            sensor = ModbusThermoSensor(
                context = context,
                master = master,
                unitId = modbusConfig.unitId ?: 0,
                address = modbusConfig.address ?: error("Modbus address is not defined for thermo sensor $name."),
                meta = sensorConfig.meta
            ),
            analyzerConfig = ThermoSensorAnalyzerConfig.combine(sensorConfig.analyzer, configuration.analyzerDefault)
        ).also { sensor ->
            deviceManager.install(name, sensor)
        }
    }

    //TODO move out of class
    override val groups: Map<String, ThermoSensorGroupAnalyzer> =
        configuration.groups.mapValues { (name, groupConfig) ->
            val sensorList = groupConfig.sensors ?: error("Group $name does not define any sensors")

            ThermoSensorGroupAnalyzer(
                context,
                sensorList.map { sensors[it] ?: error("Thermo sensor $it not found in hub") },
                groupConfig
            ).also {
                deviceManager.install(NameToken("group", name).asName(), it)
            }
        }

    override fun close() {
        masterCache.forEach {
            it.value.disconnect()
        }
    }
}