package space.kscience.controls.modbus

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.procimg.Register
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import kotlinx.io.Buffer
import space.kscience.dataforge.io.Buffer

/**
 * Convert a [Buffer] to an array of [Register]s with given [count]
 */
internal fun Buffer.toRegisters(count: Int): Array<Register> = Array(count) {
    SimpleRegister(readShort().toInt())
}

public fun AbstractModbusMaster.writeCoil(unitId: Int, key: ModbusRegistryKey.Coil, value: Boolean): Boolean =
    writeCoil(unitId, key.address, value)

public fun AbstractModbusMaster.writeHoldingRegister(unitId: Int, key: ModbusRegistryKey.HoldingRegister, value: Short): Int =
    writeSingleRegister(unitId, key.address, SimpleRegister(value.toInt()))

public fun <T> AbstractModbusMaster.writeHoldingRegisters(unitId: Int, key: ModbusRegistryKey.HoldingRange<T>, value: T): Int {
    val buffer = Buffer {
        key.format.writeTo(this, value)
    }
    return writeMultipleRegisters(unitId, key.address, buffer.toRegisters(key.count))
}

@Suppress("UNCHECKED_CAST")
public fun <T> AbstractModbusMaster.writeHolding(unitId: Int, key: ModbusRegistryKey.HoldingRegisterKey<T>, value: T): Int = when (key) {
    is ModbusRegistryKey.HoldingRegister -> writeHoldingRegister(unitId, key, value as Short)
    is ModbusRegistryKey.HoldingRange<T> -> writeHoldingRegisters(unitId, key, value)
}

/**
 * Write a value for given registry [ModbusRegistryKey] to a modbus device
 */
@Suppress("UNCHECKED_CAST")
public fun <T> AbstractModbusMaster.write(unitId: Int, key: ModbusRegistryKey<T>, value: T) {
    when (key) {
        is ModbusRegistryKey.Coil -> writeCoil(unitId, key, value as Boolean)
        is ModbusRegistryKey.HoldingRegister -> writeHoldingRegister(unitId, key, value as Short)
        is ModbusRegistryKey.HoldingRange<T> -> writeHoldingRegisters(unitId, key, value)
        is ModbusRegistryKey.DiscreteInput -> error("DiscreteInput $key is read-only")
        is ModbusRegistryKey.InputRegister -> error("InputRegister $key is read-only")
        is ModbusRegistryKey.InputRange<*> -> error("InputRange $key is read-only")
    }
}

