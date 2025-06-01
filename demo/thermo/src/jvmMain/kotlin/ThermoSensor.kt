package space.kscience.controls.demo.thermo

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import space.kscience.controls.api.Device
import space.kscience.controls.modbus.ModbusDevice
import space.kscience.controls.modbus.readInputRegister
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import kotlin.time.Duration.Companion.seconds


interface ThermoSensor : Device {

    suspend fun readTemperature(): Double

    companion object : DeviceSpec<ThermoSensor>() {
        val temperature by doubleProperty { readTemperature() }

        override suspend fun ThermoSensor.onOpen() {
            val readInterval = meta["readInterval"].double ?: 2.0
            doRecurring(readInterval.seconds) {
                read(temperature)
            }
        }

    }
}


class ModbusThermoSensor(
    context: Context,
    override val master: AbstractModbusMaster,
    override val unitId: Int,
    val address: Int,
    meta: Meta
) : ThermoSensor, ModbusDevice, DeviceBySpec<ThermoSensor>(ThermoSensor, context, meta) {
    override suspend fun readTemperature(): Double = readInputRegister(address).toDouble() / 10.0
}

