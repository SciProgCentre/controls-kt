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


internal fun CoroutineScope.launchModbusSimulator(configuration: Map<String, ThermoSensorConfig>): Job {
    val slave = ModbusSlaveFactory.createTCPSlave(9090, 10)


    return launch {
        val images = configuration.values.groupBy { it.unitId }.mapValues { (unitId, configs) ->
            SimpleProcessImage(unitId).apply {

                configs.forEach { config ->
                    val register = SimpleInputRegister(0)
                    addInputRegister(config.address, register)
                    randomWalk(config.warningThreshold - 2, 0.1).onEach {
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
): Map<String, ThermoSensorConfig> = buildMap {
    repeat(numberOfUnits) { unit ->
        repeat(sensorsPerUnit) { address ->
            put("$unit-$address", ThermoSensorConfig(unit, 1000 + address))

        }
    }
}

