package space.kscience.controls.modbus

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.procimg.InputRegister
import com.ghgande.j2mod.modbus.procimg.Register
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister
import com.ghgande.j2mod.modbus.util.BitVector
import kotlinx.io.Buffer
import space.kscience.dataforge.io.Buffer
import space.kscience.dataforge.io.ByteArray
import java.nio.ByteBuffer


/**
 * Read multiple sequential modbus coils (bit-values)
 */
public fun AbstractModbusMaster.readCoils(address: Int, count: Int, unitId: Int = 1): BitVector =
    readCoils(unitId, address, count)

public fun AbstractModbusMaster.readCoil(address: Int, unitId: Int = 1): Boolean =
    readCoils(unitId, address, 1).getBit(0)

public fun AbstractModbusMaster.read(coil: ModbusRegistryKey.Coil, unitId: Int = 1): Boolean =
    readCoil(unitId, coil.address)

public fun AbstractModbusMaster.writeCoils(address: Int, values: BooleanArray, unitId: Int = 1) {
    val bitVector = BitVector(values.size)
    values.forEachIndexed { index, value ->
        bitVector.setBit(index, value)
    }
    writeMultipleCoils(unitId, address, bitVector)
}

public fun AbstractModbusMaster.writeCoil(address: Int, value: Boolean, unitId: Int = 1) {
    writeCoil(unitId, address, value)
}

public fun AbstractModbusMaster.write(key: ModbusRegistryKey.Coil, value: Boolean, unitId: Int = 1) {
    writeCoil(unitId, key.address, value)
}

public fun AbstractModbusMaster.readInputDiscretes(address: Int, count: Int, unitId: Int = 1): BitVector =
    readInputDiscretes(unitId, address, count)

public fun AbstractModbusMaster.readInputDiscrete(address: Int, unitId: Int = 1): Boolean =
    readInputDiscretes(unitId, address, 1).getBit(0)

public fun AbstractModbusMaster.read(key: ModbusRegistryKey.DiscreteInput, unitId: Int = 1): Boolean =
    readInputDiscrete(unitId, key.address)

public fun AbstractModbusMaster.readInputRegisters(address: Int, count: Int, unitId: Int = 1): List<InputRegister> =
    readInputRegisters(unitId, address, count).toList()

public fun AbstractModbusMaster.read(key: ModbusRegistryKey.InputRegister, unitId: Int = 1): Short =
    readInputRegisters(unitId, key.address, 1).first().toShort()

private fun Array<out InputRegister>.toBuffer(): ByteBuffer {
    val buffer: ByteBuffer = ByteBuffer.allocate(size * 2)
    forEachIndexed { index, value ->
        buffer.position(index * 2)
        buffer.put(value.toBytes())
    }
    buffer.flip()
    return buffer
}

private fun Array<out InputRegister>.toPacket(): Buffer = Buffer {
    forEach { value ->
        writeShort(value.toShort())
    }
}

public fun AbstractModbusMaster.readInputRegistersToBuffer(address: Int, count: Int, unitId: Int = 1): ByteBuffer =
    readInputRegisters(unitId, address, count).toBuffer()

public fun AbstractModbusMaster.readInputRegistersToPacket(address: Int, count: Int, unitId: Int = 1): Buffer =
    readInputRegisters(unitId, address, count).toPacket()

public fun AbstractModbusMaster.readDoubleInput(address: Int, unitId: Int = 1): Double =
    readInputRegistersToBuffer(address = address, count = Double.SIZE_BYTES, unitId = unitId).getDouble()

public fun AbstractModbusMaster.readInputRegister(address: Int, unitId: Int = 1): Short =
    readInputRegisters(unitId, address, 1).first().toShort()

public fun <T> AbstractModbusMaster.read(key: ModbusRegistryKey.InputRange<T>, unitId: Int = 1): T =
    key.format.readFrom(readInputRegistersToPacket(key.address, key.count, unitId))

public fun AbstractModbusMaster.readHoldingRegisters(address: Int, count: Int, unitId: Int = 1): List<Register> =
    readMultipleRegisters(unitId, address, count).toList()

/**
 * Read a number of registers to a [ByteBuffer]
 * @param address of a register
 * @param count number of 2-bytes registers to read. Buffer size is 2*[count]
 */
public fun AbstractModbusMaster.readHoldingRegistersToBuffer(address: Int, count: Int, unitId: Int = 1): ByteBuffer =
    readMultipleRegisters(unitId, address, count).toBuffer()

public fun AbstractModbusMaster.readHoldingRegistersToPacket(address: Int, count: Int, unitId: Int = 1): Buffer =
    readMultipleRegisters(unitId, address, count).toPacket()

public fun <T> AbstractModbusMaster.read(key: ModbusRegistryKey.HoldingRange<T>, unitId: Int = 1): T =
    key.format.readFrom(readHoldingRegistersToPacket(key.address, key.count, unitId))

public fun AbstractModbusMaster.readDoubleRegister(address: Int, unitId: Int = 1): Double =
    readHoldingRegistersToBuffer(address, Double.SIZE_BYTES, unitId = unitId).getDouble()

public fun AbstractModbusMaster.readHoldingRegister(address: Int, unitId: Int = 1): Short =
    readHoldingRegisters(address, 1, unitId = unitId).first().toShort()

public fun AbstractModbusMaster.writeHoldingRegisters(address: Int, values: ShortArray, unitId: Int = 1): Int =
    writeMultipleRegisters(
        unitId,
        address,
        Array<Register>(values.size) { SimpleInputRegister(values[it].toInt()) }
    )

public fun AbstractModbusMaster.writeHoldingRegister(address: Int, value: Short, unitId: Int = 1): Int =
    writeSingleRegister(
        unitId,
        address,
        SimpleInputRegister(value.toInt())
    )

public fun AbstractModbusMaster.write(key: ModbusRegistryKey.HoldingRegister, value: Short, unitId: Int = 1): Int =
    writeHoldingRegister(key.address, value, unitId = unitId)

public fun AbstractModbusMaster.writeHoldingRegisters(address: Int, buffer: ByteBuffer, unitId: Int = 1): Int {
    val array = ShortArray(buffer.limit().floorDiv(2)) { buffer.getShort(it * 2) }

    return writeHoldingRegisters(address, array, unitId = unitId)
}

public fun AbstractModbusMaster.writeHoldingRegisters(address: Int, byteArray: ByteArray, unitId: Int = 1): Int {
    val buffer = ByteBuffer.wrap(byteArray)
    val array: ShortArray = ShortArray(buffer.limit().floorDiv(2)) { buffer.getShort(it * 2) }

    return writeHoldingRegisters(address, array, unitId = unitId)
}

public fun <T> AbstractModbusMaster.write(key: ModbusRegistryKey.HoldingRange<T>, value: T, unitId: Int = 1) {
    val buffer = ByteArray {
        key.format.writeTo(this, value)
    }

    writeHoldingRegisters(key.address, buffer, unitId = unitId)
}

