package space.kscience.controls.tagtable

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.controls.modbus.ModbusRegistryKey
import space.kscience.controls.modbus.readCoil
import space.kscience.controls.modbus.readHoldingRegister
import space.kscience.controls.modbus.readHoldingRegisters
import space.kscience.controls.modbus.readInputDiscrete
import space.kscience.controls.modbus.readInputRegister
import space.kscience.controls.modbus.readInputRegisters
import space.kscience.dataforge.io.DoubleIOFormat
import space.kscience.dataforge.io.FloatIOFormat
import space.kscience.dataforge.meta.Meta

/**
 * A property that is exposed as a Modbus register
 */
@Serializable
@SerialName("modbus")
public class ModbusTagTableColumn(
    override val source: String,
    override val timer: String,
    public val reader: ModbusPropertyReader,
    public val address: Int,
    public val unitId: Int = 1,
    override val compression: ColumnCompression? = null,
    override val meta: Meta = Meta.EMPTY,
) : TagTableColumn

@Serializable
public sealed interface ModbusPropertyReader {
    public fun read(client: AbstractModbusMaster, unitId: Int, address: Int): Meta
}

@Serializable
@SerialName("double")
public object ModbusDoubleReader : ModbusPropertyReader {

    override fun read(
        client: AbstractModbusMaster,
        unitId: Int,
        address: Int,
    ): Meta {
        val key = ModbusRegistryKey.InputRange(address = address, 4, DoubleIOFormat)
        val value = client.readInputRegisters(unitId, key)
        return Meta(value)
    }

}

@Serializable
@SerialName("short")
public object ModbusIntReader : ModbusPropertyReader {
    override fun read(
        client: AbstractModbusMaster,
        unitId: Int,
        address: Int,
    ): Meta {
        val key = ModbusRegistryKey.InputRegister(address = address)
        val value = client.readInputRegister(unitId, key)
        return Meta(value)
    }
}

/**
 * Read a [Float] from two input registers, big-endian word order
 */
@Serializable
@SerialName("float")
public object ModbusFloatReader : ModbusPropertyReader {

    override fun read(
        client: AbstractModbusMaster,
        unitId: Int,
        address: Int,
    ): Meta {
        val key = ModbusRegistryKey.InputRange(address = address, 2, FloatIOFormat)
        val value = client.readInputRegisters(unitId, key)
        return Meta(value)
    }

}

/**
 * Read a [Short] from a single holding register
 */
@Serializable
@SerialName("holding-short")
public object ModbusHoldingShortReader : ModbusPropertyReader {

    override fun read(
        client: AbstractModbusMaster,
        unitId: Int,
        address: Int,
    ): Meta {
        val key = ModbusRegistryKey.HoldingRegister(address = address)
        val value = client.readHoldingRegister(unitId, key)
        return Meta(value)
    }

}

/**
 * Read a [Float] from two holding registers
 */
@Serializable
@SerialName("holding-float")
public object ModbusHoldingFloatReader : ModbusPropertyReader {

    override fun read(
        client: AbstractModbusMaster,
        unitId: Int,
        address: Int,
    ): Meta {
        val key = ModbusRegistryKey.HoldingRange(address = address, 2, FloatIOFormat)
        val value = client.readHoldingRegisters(unitId, key)
        return Meta(value)
    }

}

/**
 * Read a [Double] from four holding registers
 */
@Serializable
@SerialName("holding-double")
public object ModbusHoldingDoubleReader : ModbusPropertyReader {

    override fun read(
        client: AbstractModbusMaster,
        unitId: Int,
        address: Int,
    ): Meta {
        val key = ModbusRegistryKey.HoldingRange(address = address, 4, DoubleIOFormat)
        val value = client.readHoldingRegisters(unitId, key)
        return Meta(value)
    }

}

/**
 * Read a [Boolean] from a single coil
 */
@Serializable
@SerialName("coil")
public object ModbusCoilReader : ModbusPropertyReader {

    override fun read(
        client: AbstractModbusMaster,
        unitId: Int,
        address: Int,
    ): Meta {
        val key = ModbusRegistryKey.Coil(address = address)
        val value = client.readCoil(unitId, key)
        return Meta(value)
    }

}

/**
 * Read a [Boolean] from a single discrete input
 */
@Serializable
@SerialName("discrete")
public object ModbusDiscreteReader : ModbusPropertyReader {

    override fun read(
        client: AbstractModbusMaster,
        unitId: Int,
        address: Int,
    ): Meta {
        val key = ModbusRegistryKey.DiscreteInput(address = address)
        val value = client.readInputDiscrete(unitId, key)
        return Meta(value)
    }

}