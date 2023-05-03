package space.kscience.controls.modbus

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.procimg.InputRegister
import com.ghgande.j2mod.modbus.procimg.Register
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.ghgande.j2mod.modbus.util.BitVector
import space.kscience.controls.api.Device
import java.nio.ByteBuffer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


/**
 * A Modbus device backed by j2mod client
 */
public interface ModbusDevice : Device {

    /**
     * Client id for this specific device
     */
    public val clientId: Int

    /**
     * The OPC-UA client initialized on first use
     */
    public val master: AbstractModbusMaster
}

/**
 * Read multiple sequential modbus coils (bit-values)
 */
public fun ModbusDevice.readCoils(ref: Int, count: Int): BitVector =
    master.readCoils(clientId, ref, count)

public fun ModbusDevice.readCoil(ref: Int): Boolean =
    master.readCoils(clientId, ref, 1).getBit(0)

public fun ModbusDevice.writeCoils(ref: Int, values: BooleanArray) {
    val bitVector = BitVector(values.size)
    values.forEachIndexed { index, value ->
        bitVector.setBit(index, value)
    }
    master.writeMultipleCoils(clientId, ref, bitVector)
}

public fun ModbusDevice.writeCoil(ref: Int, value: Boolean) {
    master.writeCoil(clientId, ref, value)
}

public fun ModbusDevice.readInputDiscretes(ref: Int, count: Int): BitVector =
    master.readInputDiscretes(clientId, ref, count)

public fun ModbusDevice.readInputRegisters(ref: Int, count: Int): List<InputRegister> =
    master.readInputRegisters(clientId, ref, count).toList()

private fun Array<out InputRegister>.toBuffer(): ByteBuffer {
    val buffer: ByteBuffer = ByteBuffer.allocate(size * 2)
    forEachIndexed { index, value ->
        buffer.position(index * 2)
        buffer.put(value.toBytes())
    }
    buffer.flip()
    return buffer
}

public fun ModbusDevice.readInputRegistersToBuffer(ref: Int, count: Int): ByteBuffer =
    master.readInputRegisters(clientId, ref, count).toBuffer()

public fun ModbusDevice.readDoubleInput(ref: Int): Double =
    readInputRegistersToBuffer(ref, Double.SIZE_BYTES).getDouble()

public fun ModbusDevice.readShortInput(ref: Int): Short =
    readInputRegisters(ref, 1).first().toShort()

public fun ModbusDevice.readHoldingRegisters(ref: Int, count: Int): List<Register> =
    master.readMultipleRegisters(clientId, ref, count).toList()

public fun ModbusDevice.readHoldingRegistersToBuffer(ref: Int, count: Int): ByteBuffer =
    master.readMultipleRegisters(clientId, ref, count).toBuffer()

public fun ModbusDevice.readDoubleRegister(ref: Int): Double =
    readHoldingRegistersToBuffer(ref, Double.SIZE_BYTES).getDouble()

public fun ModbusDevice.readShortRegister(ref: Int): Short =
    readHoldingRegisters(ref, 1).first().toShort()

public fun ModbusDevice.writeHoldingRegisters(ref: Int, values: ShortArray): Int =
    master.writeMultipleRegisters(
        clientId,
        ref,
        Array<Register>(values.size) { SimpleRegister().apply { setValue(values[it]) } }
    )

public fun ModbusDevice.writeHoldingRegister(ref: Int, value: Short): Int =
    master.writeSingleRegister(
        clientId,
        ref,
        SimpleRegister().apply { setValue(value) }
    )

public fun ModbusDevice.writeHoldingRegisters(ref: Int, buffer: ByteBuffer): Int {
    val array = ShortArray(buffer.limit().floorDiv(2)) { buffer.getShort(it * 2) }

    return writeHoldingRegisters(ref, array)
}

public fun ModbusDevice.writeShortRegister(ref: Int, value: Short) {
    master.writeSingleRegister(ref, SimpleRegister().apply { setValue(value) })
}

public fun ModbusDevice.modBusRegister(
    ref: Int,
): ReadWriteProperty<ModbusDevice, Short> = object : ReadWriteProperty<ModbusDevice, Short> {
    override fun getValue(thisRef: ModbusDevice, property: KProperty<*>): Short = readShortRegister(ref)

    override fun setValue(thisRef: ModbusDevice, property: KProperty<*>, value: Short) {
        writeHoldingRegister(ref, value)
    }
}

public fun ModbusDevice.modBusDoubleRegister(
    ref: Int,
): ReadWriteProperty<ModbusDevice, Double> = object : ReadWriteProperty<ModbusDevice, Double> {
    override fun getValue(thisRef: ModbusDevice, property: KProperty<*>): Double = readDoubleRegister(ref)

    override fun setValue(thisRef: ModbusDevice, property: KProperty<*>, value: Double) {
        val buffer = ByteBuffer.allocate(Double.SIZE_BYTES).apply { putDouble(value) }
        writeHoldingRegisters(ref, buffer)
    }
}


//
//public inline fun <reified T> ModbusDevice.opcDouble(
//): ReadWriteProperty<Any?, Double> = ma
//
//public inline fun <reified T> ModbusDeviceBySpec<*>.opcInt(
//    nodeId: NodeId,
//    magAge: Double = 1.0,
//): ReadWriteProperty<Any?, Int> = opc(nodeId, MetaConverter.int, magAge)
//
//public inline fun <reified T> ModbusDeviceBySpec<*>.opcString(
//    nodeId: NodeId,
//    magAge: Double = 1.0,
//): ReadWriteProperty<Any?, String> = opc(nodeId, MetaConverter.string, magAge)
