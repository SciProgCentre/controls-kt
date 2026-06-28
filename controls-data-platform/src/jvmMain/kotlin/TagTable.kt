package space.kscience.controls.dataplatform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import space.kscience.controls.api.DeviceMessageSource
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.api.WithLifeCycle
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.ValueStateFactory
import space.kscience.controls.dataplatform.timeseries.TimeSeriesRows
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.tables.ColumnHeader
import kotlin.time.Clock
import kotlin.time.Duration

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
public interface TagTable : ContextAware, ValueStateFactory, WithLifeCycle, DeviceMessageSource , CoroutineScope {
    /**
     * Read a value of a single column in the table
     */
    public suspend fun read(tag: String): Meta

    /**
     * Read current values of all tags
     */
    public fun readAll(): Map<String, Meta>

    /**
     * Starts generating a flow of rows for the current data platform with a specified interval.
     *
     * @param interval the interval between row generation.
     */
    public fun readTimeSeries(interval: Duration): TimeSeriesRows<Meta>

    /**
     * Create or get cached [ValueState] for a property of a [TagTable]. Only one [ValueState] with a given tag exists for the table
     */
    public fun valueState(tag: String): ValueState<Meta>

    /**
     * List all available tags and their descriptors
     */
    public val tags: Map<String, MetaDescriptor>

    public val clock: Clock


    public object ValueFactorySpec : MetaSpec() {
        public val tag: MetaRef<String> by string()
    }


    override fun build(context: Context, meta: Meta): ValueState<Meta> {
        val tag = meta[ValueFactorySpec.tag] ?: error("No tag specified")
        return valueState(tag)
    }

    override val descriptor: MetaDescriptor get() = ValueFactorySpec.descriptor

    public companion object {

        public val timeColumnHeader: ColumnHeader<Meta> = ColumnHeader<Meta>("@time") {
            title = "Time"
        }

        /**
         * A name for a property that represents a row of the table.
         */
        public const val ROW_PROPERTY_NAME: String = "@row"

        public const val TAG_TABLE_FACTORY_TYPE: String = "tagTable"
    }
}



/**
 * A value state that reads the value of a tag from a [TagTable].
 */
public class TagTableValueState(private val tagTable: TagTable, private val tag: String) : ValueState<Meta> {
    override val valueWithTime: ValueWithTime<Meta>
        get() = ValueWithTime(tagTable.readAll().get(tag) ?: Meta.EMPTY, tagTable.clock.now())

    override fun subscribeWithTime(): Flow<ValueWithTime<Meta>> =
        tagTable.messageFlow.filterIsInstance<PropertyChangedMessage>().filter { it.property == tag }.map {
            ValueWithTime(tagTable.readAll()[tag] ?: Meta.EMPTY, it.time)
        }

    override fun toString(): String = "ValueState.tagTable(tag=$tag)"
}


///**
// * Builds a device group using the provided constructor device scheme.
// *
// * @param scheme The construction scheme that defines the configuration and structure of the device group.
// * @return A new instance of DeviceGroup created based on the provided scheme and associated state factories.
// */
//public fun TagDataTable.buildDeviceGroup(
//    scheme: DeviceConfiguration
//): DeviceConstructor {
//    val valueStateFactories = ValueState.defaultValueStateFactories + (TagDataTable.PLATFORM_VALUE_FACTORY_TYPE to this)
//    return context.request(Construc)buildDeviceGroupByScheme(scheme, valueStateFactories)
//}


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
    state = platform.valueState(dataPlatformTag)
)