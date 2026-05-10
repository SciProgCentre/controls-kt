package space.kscience.controls.dataplatform

import com.fazecast.jSerialComm.SerialPort
import com.ghgande.j2mod.modbus.Modbus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.plc4x.java.api.types.PlcValueType
import space.kscience.controls.constructor.TimerState
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.time.Duration

@Serializable
public sealed interface PlatformSourceConfiguration

@Serializable
@SerialName("opc")
public data class OpcUaConfig(
    val host: String,
) : PlatformSourceConfiguration

@Serializable
public sealed interface ModbusConfig: PlatformSourceConfiguration

@Serializable
@SerialName("modbus-tcp")
public data class ModbusTcpConfig(
    val addr: String,
    val port: Int = Modbus.DEFAULT_PORT,
    val timeout: Int = Modbus.DEFAULT_TIMEOUT,
    val reconnect: Boolean = false
) : ModbusConfig

@Serializable
@SerialName("modbus-rtu")
public data class ModbusRtuConfig(
    val portName: String,
    val baudRate: Int = 9600,
    val databits: Int = 8,
    val stopbits: Int = SerialPort.ONE_STOP_BIT,
    val parity: Int = SerialPort.NO_PARITY,
    val echo: Boolean = false,    //TODO consider adding RS485 parameters
    val flowControlIn: Int = SerialPort.FLOW_CONTROL_DISABLED,
    val flowControlOut: Int = SerialPort.FLOW_CONTROL_DISABLED,
    val timeout: Int = Modbus.DEFAULT_TIMEOUT,
    val transmitDelay: Int = Modbus.DEFAULT_TRANSMIT_DELAY,
) : ModbusConfig


@Serializable
@SerialName("plc")
public data class PlcConfig(
    val address: String
) : PlatformSourceConfiguration


/**
 * Represents a platform-specific property that can be read from a data platform.
 */
@Serializable
public sealed interface PlatformProperty {
    /**
     * The name of the source
     */
    public val source: Name

    /**
     * The name of the timer that is used to read the property
     */
    public val timer: Name

    /**
     * Metadata of the property
     */
    public val meta: Meta

}


@Serializable
@SerialName("opc")
public class OpcPlatformProperty(
    override val source: Name,
    override val timer: Name,
    public val nodeId: String,
    override val meta: Meta = Meta.EMPTY,
) : PlatformProperty

@Serializable
@SerialName("plc")
public class PlcPlatformProperty(
    override val source: Name,
    override val timer: Name,
    public val address: String,
    public val plcValueType: PlcValueType,
    public val name: String = "@default",
    override val meta: Meta = Meta.EMPTY,
) : PlatformProperty

@Serializable
public sealed interface TimerConfiguration {
    public fun timer(clockManager: ClockManager): TimerState
}

@Serializable
@SerialName("fixed-rate")
public class FixedRateTimer(
    public val tick: Duration
) : TimerConfiguration {
    override fun timer(clockManager: ClockManager): TimerState = TimerState(clockManager, tick)
}


@Serializable
public class DataPlatformConfiguration(
    public val sources: Map<Name, PlatformSourceConfiguration>,
    public val timers: Map<Name, TimerConfiguration>,
    public val properties: Map<Name, PlatformProperty>,
)

