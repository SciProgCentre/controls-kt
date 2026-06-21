package space.kscience.controls.dataplatform

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.controls.modbus.ModbusRegistryKey
import space.kscience.controls.modbus.readInputRegister
import space.kscience.controls.modbus.readInputRegisters
import space.kscience.dataforge.io.DoubleIOFormat
import space.kscience.dataforge.meta.Meta

/**
 * A property that is exposed as a Modbus register
 */
@Serializable
@SerialName("modbus")
public class ModbusPlatformProperty(
    override val source: String,
    override val timer: String,
    public val reader: ModbusPropertyReader,
    public val address: Int,
    public val unitId: Int = 1,
    override val compression: ColumnCompression? = null,
    override val meta: Meta = Meta.EMPTY,
) : PlatformProperty

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