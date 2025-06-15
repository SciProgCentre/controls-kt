package center.sciprog.controls.demo.thermo

import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister
import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.invoke

internal fun randomWalk(
    initialValue: Double,
    stepSize: Double
): Flow<Double> = flow {
    var currentValue = initialValue
    while (true) {
        delay(100)
        emit(currentValue)
        val randomStep = (Math.random() * 2 - 1) * stepSize
        currentValue = (currentValue + randomStep)
    }
}


internal fun CoroutineScope.launchModbusSimulator(configuration: ThermoSensorHubConfig): Job {
    val slave = ModbusSlaveFactory.createTCPSlave(configuration.modbusDefault.port ?: 9090, configuration.sensors.size)

    val random = java.util.Random()

    return launch {
        val images = configuration.sensors.values.groupBy {
            it.modbus.unitId ?: configuration.modbusDefault.unitId ?: 0
        }.mapValues { (unitId, configs) ->
            SimpleProcessImage(unitId).apply {

                configs.forEach { sensorConfig: ThermoSensorConfig ->
                    val register = SimpleInputRegister(0)
                    addInputRegister(sensorConfig.modbus.address ?: 0, register)

                    val mu = sensorConfig.meta["simulator.mu"].double
                        ?: configuration.meta["simulator.mu"].double ?: 50.0

                    val sigma = sensorConfig.meta["simulator.sigma"].double
                        ?: configuration.meta["simulator.sigma"].double ?: 10.0

                    randomWalk(random.nextGaussian(mu, sigma), 0.1).onEach {
                        register.setValue((it * 10).toInt())
                    }.launchIn(this@launch)
                }
            }
        }

        images.forEach {
            slave.addProcessImage(it.key, it.value)
        }


        slave.open()
    }.apply {
        invokeOnCompletion {
            slave.close()
        }
    }
}

internal fun generateTestConfig(
    numberOfUnits: Int = 10,
    sensorsPerUnit: Int = 10
): ThermoSensorHubConfig = ThermoSensorHubConfig {
    sensors = buildMap {
        repeat(numberOfUnits) { unit ->
            repeat(sensorsPerUnit) { modbusAddress ->
                put("$unit-$modbusAddress", ThermoSensorConfig {
                    modbus {
                        unitId = unit
                        address = modbusAddress
                    }
                    showPlot = modbusAddress == 0
                })
            }
        }
    }

    modbusDefault {
        port = 9090
    }

    analyzerDefault {
        warningThreshold = 40.0
        alarmThreshold = 60.0
    }

    opcPort = 9091
}

