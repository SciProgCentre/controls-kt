package space.kscience.controls.tagtable

import com.fazecast.jSerialComm.SerialPort
import com.ghgande.j2mod.modbus.Modbus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.plc4x.java.api.types.PlcValueType
import space.kscience.controls.constructor.TimerState
import space.kscience.controls.storage.ZipRowsEnvelopeConverter
import space.kscience.controls.tagtable.storage.DataPlatformFileSplit
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.asValue
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A configuration for a tag source
 */
@Serializable
public sealed interface TagSourceConfig{
    /**
     * A delay between reconnection attempts
     */
    val reconnectionDelay: Duration
}

/**
 * A configuration for an OPC-UA tag source
 *
 * @param host The host address of the OPC-UA server
 * @param maxAge The maximum age of the cached tag values
 */
@Serializable
@SerialName("opc")
public data class OpcTagSourceConfig(
    val host: String,
    val maxAge: Duration = 500.milliseconds,
    override val reconnectionDelay: Duration = 5.seconds
) : TagSourceConfig

@Serializable
public sealed interface ModbusTagSourceConfig : TagSourceConfig

@Serializable
@SerialName("modbus-tcp")
public data class ModbusTcpTagSourceConfig(
    val addr: String,
    val port: Int = Modbus.DEFAULT_PORT,
    val timeout: Int = Modbus.DEFAULT_TIMEOUT,
    val reconnect: Boolean = false,
    override val reconnectionDelay: Duration = 5.seconds
) : ModbusTagSourceConfig

@Serializable
@SerialName("modbus-rtu")
public data class ModbusRtuTagSourceConfig(
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
    override val reconnectionDelay: Duration = 5.seconds
) : ModbusTagSourceConfig


@Serializable
@SerialName("plc4x")
public data class Plc4xTagSourceConfig(
    val address: String,
    override val reconnectionDelay: Duration = 5.seconds
) : TagSourceConfig

/**
 * Represents a platform-specific property that can be read from a data platform.
 *
 * @param source The name of the source
 * @param timer The name of the timer that is used to read the property
 * @param config The configuration of the column property inside the source
 * @param timeout The timeout for reading the property, defaults to 1 second
 * @param compression The compression configuration for storage of property values. If not defined, global platform compression configuration is used.
 * @param removeAuxiliaryValues Whether to remove auxiliary values like `@opc` parameters from the tag value.
 * @param maxAge The maximum age of the property value to consider it GOOD. Null means no maximum age.
 * @param maxDelta The maximum delta of the property value relative to previous one to consider it GOOD. Null means no maximum delta.
 * @param meta Metadata of the property
 */
@Serializable
public data class TagTableColumn(
    public val source: String,
    public val timer: String,
    public val config: Meta,
    public val timeout: Duration = 1.seconds,
    public val compression: ColumnCompression? = null,
    public val removeAuxiliaryValues: Boolean = true,
    public val maxAge: Duration? = null,
    public val maxDelta: Double? = null,
    public val meta: Meta = Meta.EMPTY,
) {
    public companion object
}

/**
 * A property that is read from an OPC UA server
 */
public fun TagTableColumn.Companion.opc(
    source: String,
    timer: String,
    nodeId: String,
    compression: ColumnCompression? = null,
    removeAuxiliaryValues: Boolean = true,
    maxAge: Duration? = null,
    maxDelta: Double? = null,
    meta: Meta = Meta.EMPTY,
): TagTableColumn = TagTableColumn(
    source = source,
    timer = timer,
    config = Meta {
        set(OpcTagSource.ColumnSpec.nodeId, nodeId)
    },
    compression = compression,
    removeAuxiliaryValues = removeAuxiliaryValues,
    maxAge = maxAge,
    maxDelta = maxDelta,
    meta = meta
)

/**
 * A property that is read from a Plc4X compatible source
 */
public fun TagTableColumn.Companion.plc4x(
    source: String,
    timer: String,
    address: String,
    plcValueType: PlcValueType,
    name: String? = null,
    compression: ColumnCompression? = null,
    maxAge: Duration? = null,
    maxDelta: Double? = null,
    meta: Meta = Meta.EMPTY,
): TagTableColumn = TagTableColumn(
    source = source,
    timer = timer,
    config = Meta {
        set("address", address)
        set("plcValueType", plcValueType.asValue())
        name?.let {
            set("name", name.asValue())
        }
    },
    compression = compression,
    maxAge = maxAge,
    maxDelta = maxDelta,
    meta = meta
)

/**
 * A property that is exposed as a Modbus register
 */
public fun TagTableColumn.Companion.modbus(
    source: String,
    timer: String,
    reader: ModbusTagReader,
    address: Int,
    unitId: Int = 1,
    compression: ColumnCompression? = null,
    maxAge: Duration? = null,
    maxDelta: Double? = null,
    meta: Meta = Meta.EMPTY,
): TagTableColumn = TagTableColumn(
    source = source,
    timer = timer,
    config = Meta {
        "address" put address.asValue()
        "unitId" put unitId.asValue()
        "reader" put ModbusTagReader.converter.convert(reader)
    },
    compression = compression,
    maxAge = maxAge,
    maxDelta = maxDelta,
    meta = meta
)

public fun TagTableColumn.Companion.controls(
    timer: String,
    deviceName: Name,
    propertyName: String,
    compression: ColumnCompression? = null,
    meta: Meta = Meta.EMPTY,
): TagTableColumn {

    return TagTableColumn(
        source = "@controls",
        timer = timer,
        config = Meta {
            set(ControlsTagSource.ColumnSpec.deviceName, deviceName.toString())
            set(ControlsTagSource.ColumnSpec.propertyName, propertyName)
        },
        compression = compression,
        meta = meta
    )
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
 * @property separateMeta if true place metadata to a separate file in a json instead of a single `df` container.
 */
@Serializable
public data class TagTableStorageConfiguration(
    val path: String,
    val readInterval: Duration,
    val withTagState: Boolean = false,
    val maxRowsPerEnvelope: Int = 10000,
    val maxDuration: Duration = 3.hours,
    val maxPause: Duration? = null,
    val compression: RowsCompression? = null,
    val splitStrategy: DataPlatformFileSplit = DataPlatformFileSplit.ByDate(),
    val rowsConverterType: String = ZipRowsEnvelopeConverter.meta.envelopeType,
    val separateMeta: Boolean = true,
)

/**
 * Configuration of additional tag table settings.
 *
 * @property sourceTimeOffsetGranularity Granularity of the source time offset. If null, then the source time offset is not recorded.
 */
@Serializable
public data class TagTableSettings(
    public val sourceTimeOffsetGranularity: Duration? = 1.minutes,
)


/**
 * Represents the configuration for a data platform, defining its sources, timers,
 * properties, and optional storage settings.
 *
 * @property sources A map of source identifiers to their corresponding configurations,
 *                   specifying how data is sourced for the platform.
 * @property timers A map of timer identifiers to their corresponding timer configurations,
 *                  defining the timing mechanisms used within the platform.
 * @property tags A map of property identifiers to their platform-specific properties,
 *                      used to read and manage data attributes from the platform.
 * @property storage The optional storage configuration for the platform, which manages
 *                   persistence settings such as file paths, intervals, compression, and
 *                   split strategies.
 */
@Serializable
public class TagTableConfiguration(
    public val sources: Map<String, TagSourceConfig>,
    public val timers: Map<String, TimerConfiguration>,
    public val tags: Map<String, TagTableColumn>,
    public val storage: TagTableStorageConfiguration? = null,
    public val settings: TagTableSettings? = null
)

