package center.sciprog.controls.demo.thermo

import space.kscience.dataforge.meta.*

class ThermoSensorAnalyzerConfig : Scheme() {

    var averagingWindow by int(5000)
    var warningThreshold by double(Double.POSITIVE_INFINITY)
    var alarmThreshold by double(Double.POSITIVE_INFINITY)

    companion object : SchemeSpec<ThermoSensorAnalyzerConfig>(::ThermoSensorAnalyzerConfig)
}

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

class ThermoSensorHubConfig : Scheme() {
    var modbusDefault by scheme(ThermoSensorModbusConfig)

    var analyzerDefault by scheme(ThermoSensorAnalyzerConfig)

    var sensors by meta.mapOfConvertable(ThermoSensorConfig)

    var opcPort by int(9091)

    companion object : SchemeSpec<ThermoSensorHubConfig>(::ThermoSensorHubConfig)
}