package center.sciprog.controls.demo.thermo

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import space.kscience.controls.api.Device
import space.kscience.controls.modbus.ModbusRegistryKey
import space.kscience.controls.modbus.readInputRegister
import space.kscience.controls.spec.Device
import space.kscience.controls.spec.doRecurring
import space.kscience.controls.spec.read
import space.kscience.controls.spec.reader
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import kotlin.time.Duration.Companion.seconds

//class ModbusThermoSensor(
//    context: Context,
//    override val master: AbstractModbusMaster,
//    override val unitId: Int,
//    val address: Int,
//    meta: Meta
//) : ThermoSensor, ModbusDevice, DeviceBySpec<ThermoSensor>(ThermoSensor, context, meta) {
//    override suspend fun readTemperature(): Double = readInputRegister(address).toDouble() / 10.0
//}


@Suppress("FunctionName")
fun ModbusThermoSensor(
    context: Context,
    master: AbstractModbusMaster,
    unitId: Int,
    key: ModbusRegistryKey.InputRegister,
    meta: Meta
): Device = Device(context, meta) {
    reader(ThermoSensorSpec.temperature) {
        master.readInputRegister(unitId, key).toDouble() / 10.0
    }

    onStart {
        val readInterval = meta["readInterval"].double ?: 2.0
        doRecurring(readInterval.seconds) {
            read(ThermoSensorSpec.temperature)
        }
    }
}