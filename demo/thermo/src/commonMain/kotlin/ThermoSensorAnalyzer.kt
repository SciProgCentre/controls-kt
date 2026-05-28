package center.sciprog.controls.demo.thermo

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import space.kscience.controls.api.Device
import space.kscience.controls.api.valueType
import space.kscience.controls.constructor.*
import space.kscience.controls.spec.AbstractDeviceSpec
import space.kscience.controls.spec.verify
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * An enumeration representing the operational status of a thermo sensor.
 */
@Serializable
enum class ThermoSensorStatus {
    NotConnected,
    Normal,
    Warning,
    Alarm;
    // TODO consider adding explicit messages
}

/**
 * A class for analyzing temperature data from a thermo sensor device. This class interacts with a device
 * providing temperature readings and computes various metrics such as average temperature and sensor status
 * based on thresholds and correction factors.
 *
 * @property sensor The thermo sensor device to be analyzed.
 * @property analyzerConfig Configuration settings for the analyzer, including thresholds and correction values.
 *
 * Main functionalities:
 * - Provides the current temperature reading from the sensor.
 * - Calculates the averaged temperature over a sliding time window.
 * - Determines the operational status of the sensor based on thresholds.
 * - Applies daily and yearly correction factors to thresholds.
 */
class ThermoSensorAnalyzer(
    val sensor: Device,
    val analyzerConfig: ThermoSensorAnalyzerConfig
) : DeviceConstructor(sensor.context, analyzerConfig.meta) {
    init {
        check(ThermoSensorSpec.verify(sensor)) { "Sensor is not a thermo sensor" }
        install("sensor", sensor)
    }

    val temperature = registerAsProperty(
        Companion.temperature,
        sensor.propertyAsState(ThermoSensorSpec.temperature, Double.NaN)
    )

    private val statusState = MutableValueState(ThermoSensorStatus.NotConnected)

    val status = registerAsProperty(Companion.status, statusState)

    private val averagedTemperatureState = MutableValueState(Double.NaN)

    val averageTemperature = registerAsProperty(Companion.averageTemperature, averagedTemperatureState)

    private val history = ArrayList<ValueWithTime<Double>>()

    private val mutex = Mutex()

    //cache analyzer config values

    private val daily = analyzerConfig.correction.daily

    private val yearly = analyzerConfig.correction.yearly

    private fun computeCorrection(time: LocalDateTime): Double {
        val dailyCorrection = if (daily.isNullOrEmpty()) {
            0.0
        } else {
            daily.entries.filter { it.key <= time.hour }.maxByOrNull { it.key }?.value ?: 0.0
        }

        val yearlyCorrection = if (yearly.isNullOrEmpty()) {
            0.0
        } else {
            yearly.entries.filter { it.key <= time.dayOfYear }.maxByOrNull { it.key }?.value ?: 0.0
        }

        return dailyCorrection + yearlyCorrection
    }

    private val warningThreshold = analyzerConfig.warningThreshold

    private val alarmThreshold = analyzerConfig.alarmThreshold

    private fun computeWarningThreshold(
        time: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    ): Double = warningThreshold + computeCorrection(time)

    private fun computeAlarmThreshold(
        time: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    ): Double = alarmThreshold + computeCorrection(time)

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
                average > computeAlarmThreshold() -> ThermoSensorStatus.Alarm
                average > computeWarningThreshold() -> ThermoSensorStatus.Warning
                else -> ThermoSensorStatus.Normal
            }

            statusState.value = newStatus
        }
    }

    companion object : AbstractDeviceSpec() {
        // LLM generated code: device spec for ThermoSensorAnalyzer
        val temperature by property(MetaConverter.double) {
            valueType(ValueType.NUMBER)
            description = "Temperature reading in degrees Celsius"
        }

        val status by property(MetaConverter.enum<ThermoSensorStatus>())

        val averageTemperature by property(MetaConverter.double) {
            valueType(ValueType.NUMBER)
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

    val discrepancy = registerAsProperty(
        spec = Companion.discrepancy,
        state = combineState(sensors.map { it.temperature }) { values ->
            val average = values.average()
            values.maxOf { abs(average - it) }
        }
    )

    val status = registerAsProperty(
        spec = Companion.status,
        state = discrepancy.map { discrepancy ->
            when {
                discrepancy >= config.discrepancyThreshold -> ThermoSensorStatus.Alarm
                else -> ThermoSensorStatus.Normal
            }
        }
    )

    companion object : AbstractDeviceSpec() {
        // LLM generated code: device spec for ThermoSensorGroupAnalyzer
        val discrepancy by property(MetaConverter.double) {
            valueType(ValueType.NUMBER)
        }

        val status by property(MetaConverter.enum<ThermoSensorStatus>())
    }
}