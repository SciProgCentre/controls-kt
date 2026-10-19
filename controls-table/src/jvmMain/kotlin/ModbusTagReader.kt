package space.kscience.controls.tagtable

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.controls.modbus.*
import space.kscience.dataforge.io.DoubleIOFormat
import space.kscience.dataforge.io.FloatIOFormat
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter

@Serializable
public sealed interface ModbusTagReader {
    public fun read(client: AbstractModbusMaster, unitId: Int, address: Int): Meta

    public companion object {
        public val converter: MetaConverter<ModbusTagReader> by lazy {
            MetaConverter.serializable<ModbusTagReader>()
        }
    }
}

@Serializable
@SerialName("double")
public object ModbusDoubleReader : ModbusTagReader {

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
public object ModbusIntReader : ModbusTagReader {
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
public object ModbusFloatReader : ModbusTagReader {

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
public object ModbusHoldingShortReader : ModbusTagReader {

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
public object ModbusHoldingFloatReader : ModbusTagReader {

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
public object ModbusHoldingDoubleReader : ModbusTagReader {

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
public object ModbusCoilReader : ModbusTagReader {

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
public object ModbusDiscreteReader : ModbusTagReader {

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