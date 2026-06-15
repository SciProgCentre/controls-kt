package center.sciprog.controls.demo.thermo

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.misc.DFExperimental
import kotlin.math.floor
import kotlin.time.Clock

internal fun normalize(d: Double): Double = (floor(d * 10.0) / 10.0).takeIf { it.isFinite() } ?: 0.0

/**
 * Correction for temperature sensor analyzer
 */
@OptIn(DFExperimental::class)
class ThermoSensorAnalyzerCorrectionConfig : Scheme() {
    var daily: Map<Int, Double>? by convertable(MetaConverter.serializable<Map<Int, Double>>())
    var yearly: Map<Int, Double>? by convertable(MetaConverter.serializable<Map<Int, Double>>())

    companion object : SchemeSpec<ThermoSensorAnalyzerCorrectionConfig>(::ThermoSensorAnalyzerCorrectionConfig) {}
}

class ThermoSensorAnalyzerConfig : Scheme() {

    var averagingWindow by int(5000)
    var warningThreshold by double(Double.POSITIVE_INFINITY)
    var alarmThreshold by double(Double.POSITIVE_INFINITY)

    var correction by scheme(ThermoSensorAnalyzerCorrectionConfig)

    companion object : SchemeSpec<ThermoSensorAnalyzerConfig>(::ThermoSensorAnalyzerConfig)
}

private fun ThermoSensorAnalyzerCorrectionConfig.computeCorrection(time: LocalDateTime): Double{
    val dailyCorrection = if (daily.isNullOrEmpty()) {
        0.0
    } else {
        daily?.entries?.filter { it.key <= time.hour }?.maxByOrNull{ it.key }?.value ?: 0.0
    }

    val yearlyCorrection = if (yearly.isNullOrEmpty()) {
        0.0
    } else {
        yearly?.entries?.filter { it.key <= time.dayOfYear }?.maxByOrNull { it.key }?.value ?: 0.0
    }

    return dailyCorrection + yearlyCorrection
}

fun ThermoSensorAnalyzerConfig.computeWarningThreshold(
    time: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
): Double = warningThreshold + correction.computeCorrection(time)

fun ThermoSensorAnalyzerConfig.computeAlarmThreshold(
    time: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
): Double = alarmThreshold + correction.computeCorrection(time)

class ThermoSensorModbusConfig : Scheme() {
    var host by string()
    var port by int()
    var unitId by int()

    var address by int()

    companion object : SchemeSpec<ThermoSensorModbusConfig>(::ThermoSensorModbusConfig)
}


class ThermoSensorConfig : Scheme() {
    var modbus by scheme(ThermoSensorModbusConfig)

    var analyzer by scheme(ThermoSensorAnalyzerConfig)

    var showPlot by boolean(false)

    companion object : SchemeSpec<ThermoSensorConfig>(::ThermoSensorConfig)
}

class ThermoSensorGroupConfig : Scheme() {
    var sensors by stringList()
    var discrepancyThreshold by double(5.0)

    companion object : SchemeSpec<ThermoSensorGroupConfig>(::ThermoSensorGroupConfig) {}
}

class ThermoSensorHubPlotConfig : Scheme() {
    var period by int(600)//period in seconds

    companion object : SchemeSpec<ThermoSensorHubPlotConfig>(::ThermoSensorHubPlotConfig)
}

class ThermoSensorHubConfig : Scheme() {
    var modbusDefault by scheme(ThermoSensorModbusConfig)

    var analyzerDefault by scheme(ThermoSensorAnalyzerConfig)

    var sensors by meta.mapOfConvertable(ThermoSensorConfig)

    var groups by meta.mapOfConvertable(ThermoSensorGroupConfig)

    var opcPort by int(9091)

    var plot by scheme(ThermoSensorHubPlotConfig)

    companion object : SchemeSpec<ThermoSensorHubConfig>(::ThermoSensorHubConfig)
}

fun <T : Scheme> SchemeSpec<T>.combine(primary: T, default: T): T = read(Laminate(primary.meta, default.meta))