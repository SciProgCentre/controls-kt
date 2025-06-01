package space.kscience.controls.demo.thermo

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import kotlinx.serialization.Serializable
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceHub
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.property
import space.kscience.controls.constructor.propertyAsState
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName


@Serializable
enum class ThermoSensorStatus {
    NotConnected,
    Normal,
    Warning,
    Alarm;
    // TODO consider adding explicit messages
}


@Serializable
data class ThermoSensorConfig(
    val unitId: Int,
    val address: Int,
    val warningThreshold: Double = 40.0,
    val alarmThreshold: Double = 60.0,
    val meta: Meta = Meta.EMPTY,
) {
    init {
        require(alarmThreshold > warningThreshold) { "Alarm threshold must be greater than warning threshold" }
    }
}

class ThermoSensorAnalyzer(
    val sensor: ThermoSensor,
    val description: ThermoSensorConfig
) : DeviceConstructor(sensor.context) {
    init {
        install("sensor".asName(), sensor)
    }

    val temperature by property(MetaConverter.double, sensor.propertyAsState(ThermoSensor.temperature, -100.0))

    val status by property(
        converter = MetaConverter.enum<ThermoSensorStatus>(),
        state = temperature.map {
            //TODO add analysis for history data
            when {
                it < 0.0 -> ThermoSensorStatus.NotConnected
                it > description.warningThreshold -> ThermoSensorStatus.Warning
                it > description.alarmThreshold -> ThermoSensorStatus.Alarm
                else -> ThermoSensorStatus.Normal
            }
        }
    )
}


interface ThermoSensorHub : DeviceHub, ContextAware {
    val sensors: Map<String, ThermoSensorAnalyzer>

    override val devices: Map<Name, Device> get() = sensors.mapKeys { it.key.parseAsName() }
}


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