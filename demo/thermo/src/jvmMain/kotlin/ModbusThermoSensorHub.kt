package center.sciprog.controls.demo.thermo

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.withDefault

class ModbusThermoSensorHub(
    val deviceManager: DeviceManager,
    val configuration: ThermoSensorHubConfig
) : ThermoSensorHub, AutoCloseable {

    override val context: Context get() = deviceManager.context

    private val masterCache = mutableMapOf<ThermoSensorModbusConfig, AbstractModbusMaster>()

    override val sensors: Map<String, ThermoSensorAnalyzer> = configuration.sensors.mapValues { (name, sensorConfig) ->
        val modbusConfig = sensorConfig.modbus

        val master = masterCache.getOrPut(modbusConfig) {
            ModbusTCPMaster(
                /* addr = */ modbusConfig.host ?: configuration.modbusDefault.host ?: "localhost",
                /* port = */ modbusConfig.port ?: configuration.modbusDefault.port ?: 502
            ).also {
                it.connect()
            }
        }

        ThermoSensorAnalyzer(
            sensor = ModbusThermoSensor(
                context = context,
                master = master,
                unitId = modbusConfig.unitId ?: configuration.modbusDefault.unitId ?: 0,
                address = modbusConfig.address ?: error("Modbus address is not defined for thermo sensor $name."),
                meta = sensorConfig.meta
            ),
            analyzerConfig = ThermoSensorAnalyzerConfig.read(sensorConfig.analyzer.withDefault(configuration.analyzerDefault))
        ).also { sensor ->
            deviceManager.install(name, sensor)
        }
    }

    override fun close() {
        masterCache.forEach {
            it.value.disconnect()
        }
    }
}