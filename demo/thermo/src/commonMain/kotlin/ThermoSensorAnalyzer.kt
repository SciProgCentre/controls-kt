package center.sciprog.controls.demo.thermo

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import space.kscience.controls.constructor.*
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.asName
import kotlin.time.Duration.Companion.milliseconds

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

    val temperature by property(
        MetaConverter.Companion.double,
        sensor.propertyAsState(ThermoSensor.temperature, Double.NaN)
    )

    private val statusState = MutableDeviceState(ThermoSensorStatus.NotConnected)

    val status: DeviceState<ThermoSensorStatus> by property(MetaConverter.enum<ThermoSensorStatus>(), statusState)

    private val averagedTemperatureState = MutableDeviceState(Double.NaN)

    val averageTemperature: DeviceState<Double> by property(MetaConverter.double, averagedTemperatureState)

    private val history = ArrayList<ValueWithTime<Double>>()

    private val mutex = Mutex()

    val statusUpdateJob = temperature.onNext(
        writes = listOf(status)
    ) { next ->
        if (next.isNaN()) {
            statusState.value = ThermoSensorStatus.NotConnected
            return@onNext
        }

        mutex.withLock {
            val now = clock.now()

            history.add(ValueWithTime(next, now))

            history.removeAll { now - it.time >= analyzerConfig.averagingWindow.milliseconds }

            val average = history.sumOf { it.value } / history.size.coerceAtLeast(1)

            averagedTemperatureState.value = average

            val newStatus = when {
                average > analyzerConfig.alarmThreshold -> ThermoSensorStatus.Alarm
                average > analyzerConfig.warningThreshold -> ThermoSensorStatus.Warning
                else -> ThermoSensorStatus.Normal
            }

            statusState.value = newStatus
        }
    }

//    val status by property(
//        converter = MetaConverter.Companion.enum<ThermoSensorStatus>(),
//        state = temperature.map {
//            //TODO add analysis for history data
//            when {
//                it < -100.0 || it == Double.NaN -> ThermoSensorStatus.NotConnected
//                it > analyzerConfig.alarmThreshold -> ThermoSensorStatus.Alarm
//                it > analyzerConfig.warningThreshold -> ThermoSensorStatus.Warning
//                else -> ThermoSensorStatus.Normal
//            }
//        }
//    )
}