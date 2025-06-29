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


fun CoroutineScope.launchModbusSimulator(configuration: ThermoSensorHubConfig): Job {
    val slave = ModbusSlaveFactory.createTCPSlave(configuration.modbusDefault.port ?: 9090, configuration.sensors.size)

    val random = java.util.Random()

    return launch {
        val images = configuration.sensors.values.groupBy {
            it.modbus.unitId ?: configuration.modbusDefault.unitId ?: 0
        }.mapValues { (unitId, configs) ->
            SimpleProcessImage(unitId).apply {

                val mu = configuration.meta["simulator.mu"].double
                    ?: configuration.meta["simulator.mu"].double ?: 40.0

                val sigma = configuration.meta["simulator.sigma"].double
                    ?: configuration.meta["simulator.sigma"].double ?: 10.0

                val startValue = random.nextGaussian(mu, sigma)

                configs.forEach { sensorConfig: ThermoSensorConfig ->
                    val register = SimpleInputRegister(0)
                    addInputRegister(sensorConfig.modbus.address ?: 0, register)

                    randomWalk(startValue + random.nextDouble(), 0.1).onEach {
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

fun generateTestConfig(
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

    groups = buildMap {
        sensors.entries
            .groupBy { it.key.substringBefore('-') }
            .forEach { (unit, sensorConfigs) ->
                put(unit, ThermoSensorGroupConfig {
                    sensors = sensorConfigs.map { it.key }

                    discrepancyThreshold = 5.0
                })
            }
    }

    modbusDefault {
        host = "localhost"
        port = 9090
    }

    analyzerDefault {
        warningThreshold = 40.0
        alarmThreshold = 60.0

        correction{
            daily = mapOf(0 to -2.0, 4 to -1.0, 8 to 0.0, 12 to 1.0, 16 to 0.0, 20 to -1.0)
            yearly = mapOf(0 to -2.0, 60 to -1.0, 120 to 0.0, 180 to 1.0, 260 to 0.0, 320 to -1.0)
        }
    }

    opcPort = 9091

}

