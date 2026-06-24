package space.kscience.controls.dataplatform

import kotlinx.coroutines.flow.SharedFlow
import space.kscience.controls.api.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * A device that exposes property values in a data platform
 */
public class TagTableDevice(
    public val platform: TagTable
) : Device {

    override val context: Context get() = platform.context

    override val propertyDescriptors: Collection<PropertyDescriptor> =
        platform.configuration.properties.map { (name, platformProperty) ->
            PropertyDescriptor(name)
            //TODO add type descriptors
        }

    override val actionDescriptors: Collection<ActionDescriptor> = emptyList()

    override suspend fun readProperty(propertyName: String): Meta = platform.readProperty(propertyName)

    override suspend fun writeProperty(propertyName: String, value: Meta) {
        error("Write is not supported")
    }

    override val messageFlow: SharedFlow<DeviceMessage> get() = platform.messageFlow

    override suspend fun execute(
        actionName: String,
        argument: Meta?
    ): Meta? = null

    override val clock: Clock get() = platform.clock

    override val lifecycleState: LifecycleState get() = platform.lifecycleState

    override val coroutineContext: CoroutineContext get() = platform.coroutineContext

    override suspend fun start() {
        platform.start()
    }

    override suspend fun stop() {
        platform.stop()
    }

    public companion object : DeviceFactory {
        override fun buildDevice(
            context: Context,
            meta: Meta
        ): Device {
            val tagTableName = meta["tagTable"].string
            val tagTable = context.plugins[TagTablePlugin]?.tagTables?.get(tagTableName)
                ?: error("Tag table not found for tagTable name $tagTableName")

            return TagTableDevice(tagTable)
        }
    }

}

public fun TagTable.asDevice(): Device = TagTableDevice(this)