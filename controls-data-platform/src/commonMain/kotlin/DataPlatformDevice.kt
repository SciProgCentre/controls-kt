package space.kscience.controls.dataplatform

import kotlinx.coroutines.flow.SharedFlow
import space.kscience.controls.api.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * A device that exposes property values in a data platform
 */
public class DataPlatformDevice(
    public val platform: DataPlatform
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

}

public fun DataPlatform.asDevice(): Device = DataPlatformDevice(this)