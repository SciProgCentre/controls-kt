package space.kscience.controls.dataplatform

import com.fazecast.jSerialComm.SerialPort
import com.ghgande.j2mod.modbus.Modbus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.plc4x.java.api.types.PlcValueType
import space.kscience.controls.constructor.TimerState
import space.kscience.controls.dataplatform.storage.DataPlatformFileSplit
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Serializable
public sealed interface TagTableSourceConfig

@Serializable
@SerialName("opc")
public data class OpcUaConfig(
    val host: String,
) : TagTableSourceConfig

@Serializable
public sealed interface ModbusConfig : TagTableSourceConfig

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
@SerialName("plc4x")
public data class Plc4xConfig(
    val address: String
) : TagTableSourceConfig

/**
 * Represents a platform-specific property that can be read from a data platform.
 */
@Serializable
public sealed interface TagTableColumn {
    /**
     * The name of the source
     */
    public val source: String

    /**
     * The name of the timer that is used to read the property
     */
    public val timer: String

    /**
     * Metadata of the property
     */
    public val meta: Meta

    /**
     * The timeout for reading the property, defaults to 1 second
     */
    public val timeout: Duration get() = 1.seconds

    /**
     * The compression configuration for storage of property values.
     * If not defined, global platform compression configuration is used.
     */
    public val compression: ColumnCompression?
}

/**
 * A property that is read from an OPC UA server
 */
@Serializable
@SerialName("opc")
public class OpcTagTableColumn(
    override val source: String,
    override val timer: String,
    public val nodeId: String,
    override val compression: ColumnCompression? = null,
    override val meta: Meta = Meta.EMPTY,
) : TagTableColumn

/**
 * A property that is read from a Plc4X compatible source
 */
@Serializable
@SerialName("plc")
public class PlcTagTableColumn(
    override val source: String,
    override val timer: String,
    public val address: String,
    public val plcValueType: PlcValueType,
    public val name: String = "@default",
    override val compression: ColumnCompression? = null,
    override val meta: Meta = Meta.EMPTY,
) : TagTableColumn


@Serializable
@SerialName("controls")
public class InternalTagTableColumn(
    override val timer: String,
    public val deviceName: Name,
    public val propertyName: String,
    override val compression: ColumnCompression? = null,
    override val meta: Meta = Meta.EMPTY,
) : TagTableColumn{
    override val source: String get() = "@controls"
}

@Serializable
public sealed interface TimerConfiguration {
    public fun createTimerState(clockManager: ClockManager): TimerState
}

@Serializable
@SerialName("fixed-rate")
public class FixedRateTimer(
    public val tick: Duration
) : TimerConfiguration {
    override fun createTimerState(clockManager: ClockManager): TimerState = TimerState(clockManager, tick)
}

/**
 * Represents the configuration settings for platform storage, providing control over file paths,
 * data partitioning, compression, and other storage-related parameters.
 *
 * @property path The base directory path where platform data files will be stored.
 * @property readInterval The interval between successive read operations from the storage.
 * @property maxRowsPerEnvelope The maximum number of rows to be included in a single data envelope.
 *                              Defaults to 10,000 rows.
 * @property maxDuration The maximum time interval for storing data before triggering a flush or split operation.
 *                       Defaults to 3 hours.
 * @property maxPause The maximum allowable pause duration between storage operations to create a new file.
 * @property compression Optional compression settings to be applied to the stored rows.
 * @property splitStrategy The strategy for organizing data into subdirectories, such as by date or by hour.
 */
@Serializable
public data class TagTableStorageConfiguration(
    val path: String,
    val readInterval: Duration,
    val maxRowsPerEnvelope: Int = 10000,
    val maxDuration: Duration = 3.hours,
    val maxPause: Duration? = null,
    val compression: RowsCompression? = null,
    val splitStrategy: DataPlatformFileSplit = DataPlatformFileSplit.ByDate(),
)

/**
 * Represents the configuration for a data platform, defining its sources, timers,
 * properties, and optional storage settings.
 *
 * @property sources A map of source identifiers to their corresponding configurations,
 *                   specifying how data is sourced for the platform.
 * @property timers A map of timer identifiers to their corresponding timer configurations,
 *                  defining the timing mechanisms used within the platform.
 * @property properties A map of property identifiers to their platform-specific properties,
 *                      used to read and manage data attributes from the platform.
 * @property storage The optional storage configuration for the platform, which manages
 *                   persistence settings such as file paths, intervals, compression, and
 *                   split strategies.
 */
@Serializable
public class PlcTableConfiguration(
    public val sources: Map<String, TagTableSourceConfig>,
    public val timers: Map<String, TimerConfiguration>,
    public val properties: Map<String, TagTableColumn>,
    public val storage: TagTableStorageConfiguration? = null,
)

