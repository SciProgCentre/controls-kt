package space.kscience.controls.dataplatform

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.controls.modbus.ModbusRegistryKey
import space.kscience.controls.modbus.readInputRegisters
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.io.DoubleIOFormat
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name

@Serializable
@SerialName("modbus")
public class ModbusPlatformProperty(
    override val source: Name,
    override val timer: Name,
    public val reader: ModbusPropertyReader,
    public val address: Int,
    public val unitId: Int = 1,
) : PlatformProperty {
    override suspend fun read(platform: DataPlatform): ValueWithTime<Meta> {
        val client = platform.resolveModbusClient(source) ?: error("No Modbus client found for $source")

        val meta = reader.read(client, unitId, address)

        return ValueWithTime(meta, platform.clock.now())
    }
}

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