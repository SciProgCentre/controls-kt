package center.sciprog.controls.demo.thermo

import kotlinx.serialization.Serializable
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceHub
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.property
import space.kscience.controls.constructor.propertyAsState
import space.kscience.dataforge.context.ContextAware
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


class ThermoSensorAnalyzer(
    val sensor: ThermoSensor,
    val analyzerConfig: ThermoSensorAnalyzerConfig
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
                it < -100.0 -> ThermoSensorStatus.NotConnected
                it > analyzerConfig.warningThreshold -> ThermoSensorStatus.Warning
                it > analyzerConfig.alarmThreshold -> ThermoSensorStatus.Alarm
                else -> ThermoSensorStatus.Normal
            }
        }
    )
}


interface ThermoSensorHub : DeviceHub, ContextAware {
    val sensors: Map<String, ThermoSensorAnalyzer>

    override val devices: Map<Name, Device> get() = sensors.mapKeys { it.key.parseAsName() }
}