package space.kscience.controls.timeseries

import com.fazecast.jSerialComm.SerialPort
import com.ghgande.j2mod.modbus.Modbus
import kotlinx.coroutines.future.await
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.plc4x.java.api.types.PlcValueType
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import space.kscience.controls.modbus.ModbusRegistryKey
import space.kscience.controls.modbus.read
import space.kscience.controls.opcua.client.readValueWithTime
import space.kscience.controls.plc4x.Plc4xProperty
import space.kscience.controls.plc4x.throwOnFail
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name


public sealed interface PlatformSourceConfig

@Serializable
@SerialName("opc")
public data class OpcUaConfig(
    val host: String,
) : PlatformSourceConfig

@Serializable
@SerialName("modbus-tcp")
public data class ModbusTcpConfig(
    val addr: String,
    val port: Int = Modbus.DEFAULT_PORT,
    val timeout: Int = Modbus.DEFAULT_TIMEOUT,
    val reconnect: Boolean = false
) : PlatformSourceConfig

@Serializable
@SerialName("modbus-rtu")
public data class ModbusRtuConfig(
    val portName: String,
    val baudRate: Int = 9600,
    val flowControlIn: Int = SerialPort.FLOW_CONTROL_DISABLED,
    val flowControlOut: Int = SerialPort.FLOW_CONTROL_DISABLED,
    val databits: Int = 8,
    val stopbits: Int = SerialPort.ONE_STOP_BIT,
    val parity: Int = SerialPort.NO_PARITY,
    val echo: Boolean = false,    //TODO consider adding RS485 parameters
    val timeout: Int = Modbus.DEFAULT_TIMEOUT,
    val transmitDelay: Int = Modbus.DEFAULT_TRANSMIT_DELAY,
) : PlatformSourceConfig


@Serializable
@SerialName("plc")
public data class PlcConfig(
    val address: String
) : PlatformSourceConfig


@Serializable
public sealed interface PlatformProperty {
    public val source: Name

    public suspend fun read(platform: DataPlatform): ValueWithTime<Meta>
}


@Serializable
@SerialName("modbus")
public class ModbusPlatformProperty<T>(
    override val source: Name,
    public val key: ModbusRegistryKey<T>,
    public val converter: MetaConverter<T>,
    public val unitId: Int = 1,
) : PlatformProperty {
    override suspend fun read(platform: DataPlatform): ValueWithTime<Meta> {
        val client = platform.resolveModbusClient(source) ?: error("No Modbus client found for $source")

        val value = client.read(unitId, key)

        val meta = converter.convert(value)
        return ValueWithTime(meta, platform.clock.now())
    }
}


@Serializable
@SerialName("opc")
public class OpcPlatformProperty(
    override val source: Name,
    public val nodeId: String
) : PlatformProperty {
    override suspend fun read(platform: DataPlatform): ValueWithTime<Meta> {
        val client = platform.resolveOpcClient(source) ?: error("No OPC client found for $source")
        return client.readValueWithTime(NodeId.parse(nodeId), MetaConverter.meta)
    }
}

@Serializable
@SerialName("plc")
public class PlcPlatformProperty(
    override val source: Name,
    public val address: String,
    public val plcValueType: PlcValueType,
    public val name: String = "@default",
) : PlatformProperty {
    override suspend fun read(platform: DataPlatform): ValueWithTime<Meta> {
        val connection = platform.resolvePlcClient(source) ?: error("No PLC client found")

        require(connection.metadata.isReadSupported) { "Read actions are not supported on connections" }

        with(Plc4xProperty(address, plcValueType, name)) {
            val request = connection.readRequestBuilder().request().build()
            val response = request.execute().await()
            response.throwOnFail()

            val time = response.getDateTime(name).toKotlinLocalDateTime().toInstant(platform.timeZone)
            val value = response.readProperty()
            return ValueWithTime(value, time)
        }
    }
}



@Serializable
public class DataPlatformConfiguration(
    public val sources: Map<Name, PlatformSourceConfig>,
    public val properties: Map<Name, PlatformProperty>,
)

