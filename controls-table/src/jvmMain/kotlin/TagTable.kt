package space.kscience.controls.tagtable

import kotlinx.coroutines.flow.*
import space.kscience.controls.api.*
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.tagtable.timeseries.TimeSeriesRows
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.tables.ColumnHeader
import kotlin.time.Duration

@JvmInline
public value class TagState(public val value: Meta) {

    public val quality: String? get() = value[TagState.quality]

    public companion object : MetaSpec() {

        public val quality: MetaRef<String> by string()

        public val EMPTY: TagState = TagState(Meta.EMPTY)

        public const val TAG_QUALITY_SUFFIX: String = ".@quality"

        public const val GOOD_QUALITY: String = "GOOD"

        public const val BAD_QUALITY: String = "BAD"

        public const val READ_FAILED_QUALITY: String = "READ_FAILED"

        public const val NOT_CONNECTED_QUALITY: String = "NOT_CONNECTED"
    }
}

/**
 * Represents a table of tags that provides a mechanism to interact with
 * dynamically changing values in a data platform. This interface enables
 * reading, monitoring, and managing these values as time series data or
 * stateful properties.
 *
 * It extends the following:
 * - `ContextAware`: Ensures access to a `Context` for configuration and operation.
 * - `WithLifeCycle`: Provides lifecycle management such as starting and stopping the table.
 * - `DeviceMessageSource`: Allows access to device messages via a shared flow.
 * - `ValueStateFactory`: Enables the creation and management of observable value states.
 */
public interface TagTable : CachingDevice {
    /**
     * Read a value of a single column in the table
     */
    public suspend fun readTag(tag: String): Meta

    /**
     * Read the current cached sample with time. Unknown sample time is represented by [Instant.DISTANT_PAST].
     */
    public fun readTagWithTime(tag: String): ValueWithTime<Meta>

    /**
     * Read a state of a tag single column in the table
     */
    public suspend fun readTagQuality(tag: String): TagState

    /**
     * Read current values of all tags
     */
    public fun readAllValues(): Map<String, Meta>

    /**
     * Starts generating a flow of rows for the current data platform with a specified interval.
     *
     * @param interval the interval between row generation.
     * @param withTagQuality whether to include tag state in the rows and headers.
     * Quality column names append [TagState.TAG_QUALITY_SUFFIX] to the tag name.
     * @param includeOldValues if false values older than previous read time are not written
     */
    public fun readTimeSeries(
        interval: Duration,
        withTagQuality: Boolean = false,
        includeOldValues: Boolean = true
    ): TimeSeriesRows<Meta>

    /**
     * Create or get cached [ValueState] for a property of a [TagTable]. Only one [ValueState] with a given tag exists for the table
     */
    public fun subscribe(tag: String): ValueState<Meta>

    /**
     * List all available tags and their descriptors
     */
    public val tags: Map<String, MetaDescriptor>

    /**
     * Tag table messages. Shared so that a subscriber can be attached atomically with reading the current value.
     */
    override val messageFlow: SharedFlow<DeviceMessage>

    public companion object : DeviceFactory {

        override fun buildDevice(
            context: Context,
            attributes: Meta
        ): TagTable {
            val configuration = MetaConverter.serializable<TagTableConfiguration>().read(attributes)

            return context.request(TagTablePlugin).install(configuration)
        }


        public val timeColumnHeader: ColumnHeader<Meta> = ColumnHeader<Meta>("@time") {
            title = "Time"
        }

        public const val TAG_TABLE_FACTORY_TYPE: String = "tagTable"
    }
}

/**
 * Read all values with tag states from a [TagTable].
 */
public suspend fun TagTable.readAllValuesWithTagState(): Map<String, Meta> {
    val allValues = readAllValues()
    val tagStates = allValues.keys.associate { tag -> (tag + TagState.TAG_QUALITY_SUFFIX) to readTagQuality(tag).value }
    return allValues + tagStates
}


/**
 * Register a device property that is bound to a [TagTable] source.
 */
public fun DeviceConstructor.tagTableProperty(
    platform: TagTable,
    propertyName: String,
    dataPlatformTag: String = propertyName,
    description: String? = null,
): ValueState<Meta> = registerProperty(
    converter = MetaConverter.meta,
    descriptor = PropertyDescriptor(propertyName, description),
    state = platform.subscribe(dataPlatformTag)
)
