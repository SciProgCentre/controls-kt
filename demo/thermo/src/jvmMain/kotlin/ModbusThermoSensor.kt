package center.sciprog.controls.demo.thermo

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import space.kscience.controls.modbus.ModbusDevice
import space.kscience.controls.modbus.readInputRegister
import space.kscience.controls.spec.DeviceBySpec
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta

class ModbusThermoSensor(
    context: Context,
    override val master: AbstractModbusMaster,
    override val unitId: Int,
    val address: Int,
    meta: Meta
) : ThermoSensor, ModbusDevice, DeviceBySpec<ThermoSensor>(ThermoSensor, context, meta) {
    override suspend fun readTemperature(): Double = readInputRegister(address).toDouble() / 10.0
}