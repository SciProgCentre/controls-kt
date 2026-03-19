package space.kscience.controls.modbus

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.procimg.InputRegister
import kotlinx.io.Buffer
import space.kscience.dataforge.io.Buffer
import java.nio.ByteBuffer

/**
 * Convert array of input registers to a nio buffer
 */
internal fun Array<out InputRegister>.toByteBuffer(): ByteBuffer {
    val buffer: ByteBuffer = ByteBuffer.allocate(size * 2)
    forEachIndexed { index, value ->
        buffer.position(index * 2)
        buffer.put(value.toBytes())
    }
    buffer.flip()
    return buffer
}

/**
 * Convert array of input registers to a kotlinx-io buffer
 */
internal fun Array<out InputRegister>.toBuffer(): Buffer = Buffer {
    forEach { value ->
        writeShort(value.toShort())
    }
}


public fun AbstractModbusMaster.readCoil(unitId: Int, key: ModbusRegistryKey.Coil): Boolean =
    readCoils(unitId, key.address, 1).getBit(0)

public fun AbstractModbusMaster.readInputDiscrete(unitId: Int, key: ModbusRegistryKey.DiscreteInput): Boolean =
    readInputDiscretes(unitId, key.address, 1).getBit(0)

public fun AbstractModbusMaster.readInputRegister(unitId: Int, key: ModbusRegistryKey.InputRegister): Short =
    readInputRegisters(unitId, key.address, 1).first().toShort()

public fun <T> AbstractModbusMaster.readInputRegisters(unitId: Int, key: ModbusRegistryKey.InputRange<T>): T =
    readInputRegisters(unitId, key.address, key.count).toBuffer().let(key.format::readFrom)

public fun AbstractModbusMaster.readHoldingRegister(unitId: Int,key: ModbusRegistryKey.HoldingRegister): Short =
    readMultipleRegisters(unitId, key.address, 1).first().toShort()

public fun <T> AbstractModbusMaster.readHoldingRegisters(unitId: Int, key: ModbusRegistryKey.HoldingRange<T>): T =
    readMultipleRegisters(unitId, key.address, key.count).toBuffer().let(key.format::readFrom)

/**
 * Read a value for given registry [ModbusRegistryKey] from a modbus device
 */
@Suppress("UNCHECKED_CAST")
public fun <T> AbstractModbusMaster.read(unitId: Int, key: ModbusRegistryKey<T>): T = when (key) {
    is ModbusRegistryKey.Coil -> readCoil(unitId, key)
    is ModbusRegistryKey.DiscreteInput -> readInputDiscrete(unitId, key)
    is ModbusRegistryKey.HoldingRegister -> readHoldingRegister(unitId, key)
    is ModbusRegistryKey.HoldingRange<T> -> readHoldingRegisters(unitId, key)
    is ModbusRegistryKey.InputRegister -> readInputRegister(unitId, key)
    is ModbusRegistryKey.InputRange<T> -> readInputRegisters(unitId, key)
} as T