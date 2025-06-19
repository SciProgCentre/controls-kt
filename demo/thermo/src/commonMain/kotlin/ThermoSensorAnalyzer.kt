package center.sciprog.controls.demo.thermo

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import space.kscience.controls.api.valueType
import space.kscience.controls.constructor.*
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.names.asName
import kotlin.math.abs
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
) : DeviceConstructor(sensor.context, analyzerConfig.meta) {
    init {
        install("sensor".asName(), sensor)
    }

    val temperature by property(
        converter = MetaConverter.Companion.double,
        state = sensor.propertyAsState(ThermoSensor.temperature, Double.NaN),
        descriptorBuilder = {
            valueType(ValueType.NUMBER)
        }
    )

    private val statusState = MutableDeviceState(ThermoSensorStatus.NotConnected)

    val status: DeviceState<ThermoSensorStatus> by property(MetaConverter.enum<ThermoSensorStatus>(), statusState)

    private val averagedTemperatureState = MutableDeviceState(Double.NaN)

    val averageTemperature: DeviceState<Double> by property(
        converter = MetaConverter.double,
        state = averagedTemperatureState,
        descriptorBuilder = {
            valueType(ValueType.NUMBER)
        }
    )

    private val history = ArrayList<ValueWithTime<Double>>()

    private val mutex = Mutex()

    private val statusUpdateJob = temperature.onNext(
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
                average > analyzerConfig.computeAlarmThreshold() -> ThermoSensorStatus.Alarm
                average > analyzerConfig.computeWarningThreshold() -> ThermoSensorStatus.Warning
                else -> ThermoSensorStatus.Normal
            }

            statusState.value = newStatus
        }
    }
}

/**
 * A group-level analyzer for multiple thermal sensors. This class aggregates and monitors
 * the states of a collection of `ThermoSensorAnalyzer` instances, providing insights and
 * meta-state calculations based on the group-wide behavior.
 *
 * @constructor Creates a new instance with the provided context, list of analyzers, and
 * configuration settings.
 * @param context The system context under which the analyzer operates.
 * @param sensors A collection of `ThermoSensorAnalyzer` instances, each representing a
 * sensor to be monitored as part of the group.
 * @param config A configuration object defining the parameters for the group-level
 * analysis, including thresholds and metadata.
 *
 * Properties:
 * - `discrepancy`: A computed property representing the absolute maximum deviation of any
 * sensor's temperature from the group's average temperature. Helps in identifying significant
 * sensor outliers in the group.
 * - `status`: A computed property reflecting the overall status of the group, based on the
 * discrepancy between individual sensor readings. The status is determined using the
 * discrepancy threshold defined in the `ThermoSensorGroupConfig`. Possible statuses include
 * `Normal` and `Alarm`.
 */
class ThermoSensorGroupAnalyzer(
    context: Context,
    val sensors: List<ThermoSensorAnalyzer>,
    val config: ThermoSensorGroupConfig
) : DeviceConstructor(context, config.meta) {

    val discrepancy by property(
        converter = MetaConverter.double,
        state = combineState(sensors.map { it.temperature }) { values ->
            val average = values.average()
            values.maxOf { abs(average - it) }
        },
        descriptorBuilder = {
            valueType(ValueType.NUMBER)
        }
    )

    val status by property(
        converter = MetaConverter.enum<ThermoSensorStatus>(),
        state = discrepancy.map { discrepancy ->
            when {
                discrepancy >= config.discrepancyThreshold -> ThermoSensorStatus.Alarm
                else -> ThermoSensorStatus.Normal
            }
        }
    )
}