package space.kscience.controls.modbus

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.procimg.InputRegister
import com.ghgande.j2mod.modbus.procimg.Register
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister
import com.ghgande.j2mod.modbus.util.BitVector
import kotlinx.io.Buffer
import space.kscience.controls.api.Device
import space.kscience.dataforge.io.Buffer
import space.kscience.dataforge.io.ByteArray
import java.nio.ByteBuffer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


/**
 * A Modbus device backed by j2mod client
 */
public interface ModbusDevice : Device {

    /**
     * Unit id for this specific device
     */
    public val unitId: Int

    /**
     * The modubus master connector
     */
    public val master: AbstractModbusMaster

    public operator fun ModbusRegistryKey.Coil.getValue(thisRef: Any?, property: KProperty<*>): Boolean =
        readCoil(address)

    public operator fun ModbusRegistryKey.Coil.setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        writeCoil(address, value)
    }

    public operator fun ModbusRegistryKey.DiscreteInput.getValue(thisRef: Any?, property: KProperty<*>): Boolean =
        readInputDiscrete(address)

    public operator fun ModbusRegistryKey.InputRegister.getValue(thisRef: Any?, property: KProperty<*>): Short =
        readInputRegister(address)

    public operator fun <T> ModbusRegistryKey.InputRange<T>.getValue(thisRef: Any?, property: KProperty<*>): T {
        val packet = readInputRegistersToPacket(address, count)
        return format.readFrom(packet)
    }


    public operator fun ModbusRegistryKey.HoldingRegister.getValue(thisRef: Any?, property: KProperty<*>): Short =
        readHoldingRegister(address)

    public operator fun ModbusRegistryKey.HoldingRegister.setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: Short,
    ) {
        writeHoldingRegister(address, value)
    }

    public operator fun <T> ModbusRegistryKey.HoldingRange<T>.getValue(thisRef: Any?, property: KProperty<*>): T {
        val packet = readHoldingRegistersToPacket(address, count)
        return format.readFrom(packet)
    }

    public operator fun <T> ModbusRegistryKey.HoldingRange<T>.setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T,
    ) {
        val buffer = ByteArray {
            format.writeTo(this, value)
        }
        writeHoldingRegisters(address, buffer)
    }

}

/**
 * Read multiple sequential modbus coils (bit-values)
 */
public fun ModbusDevice.readCoils(address: Int, count: Int): BitVector =
    master.readCoils(unitId, address, count)

public fun ModbusDevice.readCoil(address: Int): Boolean =
    master.readCoils(unitId, address, 1).getBit(0)

public fun ModbusDevice.writeCoils(address: Int, values: BooleanArray) {
    val bitVector = BitVector(values.size)
    values.forEachIndexed { index, value ->
        bitVector.setBit(index, value)
    }
    master.writeMultipleCoils(unitId, address, bitVector)
}

public fun ModbusDevice.writeCoil(address: Int, value: Boolean) {
    master.writeCoil(unitId, address, value)
}

public fun ModbusDevice.writeCoil(key: ModbusRegistryKey.Coil, value: Boolean) {
    master.writeCoil(unitId, key.address, value)
}

public fun ModbusDevice.readInputDiscretes(address: Int, count: Int): BitVector =
    master.readInputDiscretes(unitId, address, count)

public fun ModbusDevice.readInputDiscrete(address: Int): Boolean =
    master.readInputDiscretes(unitId, address, 1).getBit(0)

public fun ModbusDevice.readInputRegisters(address: Int, count: Int): List<InputRegister> =
    master.readInputRegisters(unitId, address, count).toList()

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

public fun ModbusDevice.readInputRegistersToBuffer(address: Int, count: Int): ByteBuffer =
    master.readInputRegisters(unitId, address, count).toBuffer()

public fun ModbusDevice.readInputRegistersToPacket(address: Int, count: Int): Buffer =
    master.readInputRegisters(unitId, address, count).toPacket()

public fun ModbusDevice.readDoubleInput(address: Int): Double =
    readInputRegistersToBuffer(address, Double.SIZE_BYTES).getDouble()

public fun ModbusDevice.readInputRegister(address: Int): Short =
    readInputRegisters(address, 1).first().toShort()

public fun ModbusDevice.readHoldingRegisters(address: Int, count: Int): List<Register> =
    master.readMultipleRegisters(unitId, address, count).toList()

/**
 * Read a number of registers to a [ByteBuffer]
 * @param address of a register
 * @param count number of 2-bytes registers to read. Buffer size is 2*[count]
 */
public fun ModbusDevice.readHoldingRegistersToBuffer(address: Int, count: Int): ByteBuffer =
    master.readMultipleRegisters(unitId, address, count).toBuffer()

public fun ModbusDevice.readHoldingRegistersToPacket(address: Int, count: Int): Buffer =
    master.readMultipleRegisters(unitId, address, count).toPacket()

public fun ModbusDevice.readDoubleRegister(address: Int): Double =
    readHoldingRegistersToBuffer(address, Double.SIZE_BYTES).getDouble()

public fun ModbusDevice.readHoldingRegister(address: Int): Short =
    readHoldingRegisters(address, 1).first().toShort()

public fun ModbusDevice.writeHoldingRegisters(address: Int, values: ShortArray): Int =
    master.writeMultipleRegisters(
        unitId,
        address,
        Array<Register>(values.size) { SimpleInputRegister(values[it].toInt()) }
    )

public fun ModbusDevice.writeHoldingRegister(address: Int, value: Short): Int =
    master.writeSingleRegister(
        unitId,
        address,
        SimpleInputRegister(value.toInt())
    )

public fun ModbusDevice.writeHoldingRegister(key: ModbusRegistryKey.HoldingRegister, value: Short): Int =
    writeHoldingRegister(key.address, value)

public fun ModbusDevice.writeHoldingRegisters(address: Int, buffer: ByteBuffer): Int {
    val array: ShortArray = ShortArray(buffer.limit().floorDiv(2)) { buffer.getShort(it * 2) }

    return writeHoldingRegisters(address, array)
}

public fun ModbusDevice.writeHoldingRegisters(address: Int, byteArray: ByteArray): Int {
    val buffer = ByteBuffer.wrap(byteArray)
    val array: ShortArray = ShortArray(buffer.limit().floorDiv(2)) { buffer.getShort(it * 2) }

    return writeHoldingRegisters(address, array)
}

public fun ModbusDevice.modbusRegister(
    address: Int,
): ReadWriteProperty<ModbusDevice, Short> = object : ReadWriteProperty<ModbusDevice, Short> {
    override fun getValue(thisRef: ModbusDevice, property: KProperty<*>): Short = readHoldingRegister(address)

    override fun setValue(thisRef: ModbusDevice, property: KProperty<*>, value: Short) {
        writeHoldingRegister(address, value)
    }
}

public fun ModbusDevice.modbusDoubleRegister(
    address: Int,
): ReadWriteProperty<ModbusDevice, Double> = object : ReadWriteProperty<ModbusDevice, Double> {
    override fun getValue(thisRef: ModbusDevice, property: KProperty<*>): Double = readDoubleRegister(address)

    override fun setValue(thisRef: ModbusDevice, property: KProperty<*>, value: Double) {
        val buffer = ByteBuffer.allocate(Double.SIZE_BYTES).apply { putDouble(value) }
        writeHoldingRegisters(address, buffer)
    }
}
