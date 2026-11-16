package space.kscience.controls.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.propertyMessageFlow
import space.kscience.dataforge.context.*
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.Laminate
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.isEmpty
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.plus
import kotlin.time.Duration

public class ControlsStoragePlugin : AbstractPlugin() {
    public val io: IOPlugin by require(IOPlugin)

    override val tag: PluginTag get() = Companion.tag

    public val rowEnvelopeConverters: Map<String, RowsEnvelopeConverter<Meta>> by lazy {
        context.gather<RowsEnvelopeConverter<Meta>>(RowsEnvelopeConverter.ROWS_ENVELOPE_CONVERTER_TARGET).values.associateBy { it.envelopeType }
    }

    public val messageStorageFactories: Map<String, Factory<DeviceMessageStorage>> by lazy {
        context.gather<Factory<DeviceMessageStorage>>(MESSAGE_STORAGE_FACTORY_TARGET).mapKeys { it.toString() }
    }

    override fun content(target: String): Map<Name, Any> = when (target) {
        RowsEnvelopeConverter.ROWS_ENVELOPE_CONVERTER_TARGET -> mapOf(
            Name.of("zip") to ZipRowsEnvelopeConverter.meta,
            Name.of("plain") to PlainRowsEnvelopeConverter.meta
        )

        MESSAGE_STORAGE_FACTORY_TARGET -> mapOf(
            Name.of("file") to FileDeviceMessageStorage
        )

        else -> super.content(target)
    }


    public companion object : PluginFactory<ControlsStoragePlugin> {
        override val tag: PluginTag = PluginTag("controls.storage")

        override fun build(
            context: Context,
            meta: Meta
        ): ControlsStoragePlugin = ControlsStoragePlugin()

        public const val MESSAGE_STORAGE_FACTORY_TARGET: String = "controls.storage.messageStorageFactory"
    }
}

/**
 * Debounce property change message and leave all other messages unchanged
 */
@OptIn(FlowPreview::class)
private fun Flow<PropertyChangedMessage>.filterPropertyChanges(interval: Duration?): Flow<PropertyChangedMessage> =
    if (interval == null) {
        //no debounce
        this
    } else {
        sample(interval)
    }


/**
 * Launches a coroutine job that stores device messages from this device tree into configured writable storages.
 *
 * The job recursively traverses the device tree, inspects storage configuration metadata for each device and
 * readable property descriptor, and subscribes to the corresponding message flows. Device-level storage captures
 * non-property-change messages, while property-level storage captures property change messages filtered according
 * to the configured sample interval. Message sources are rewritten to include the hierarchical device name before
 * being written.
 *
 * If a referenced storage name is not present in [storageMap], the launched job fails with an error. Write failures
 * are logged.
 *
 * @param storageMap map of storage names to writable device message storages used by storage configurations.
 * @param coroutineScope coroutine scope in which the storage job and its flow collection coroutines are launched.
 * @param storageConfig optional fallback or overriding metadata used together with device and property metadata to
 * resolve message storage configuration.
 * @return the launched coroutine [Job] responsible for configuring and running storage subscriptions for this
 * device tree.
 */
public fun DeviceTree.launchStorageJob(
    storageMap: Map<String, WriteableDeviceMessageStorage>,
    coroutineScope: CoroutineScope,
    storageConfig: Meta? = null
): Job = coroutineScope.launch {

    fun Device.storeDeviceProperties(deviceName: Name) {

        val deviceStorageMeta = Laminate(meta, storageConfig)[MessageStorageScheme.MESSAGE_STORAGE_KEY]

        if (deviceStorageMeta != null && !deviceStorageMeta.isEmpty()) {
            val storageConfiguration = MessageStorageScheme.read(deviceStorageMeta)
            val storageName = storageConfiguration.storage

            val storage = storageMap[storageName] ?: error("Storage with name $storageName is not defined")

            messageFlow.filter { it !is PropertyChangedMessage }.onEach { message ->
                try {
                    storage.write(message.changeSource { deviceName + it })
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        currentCoroutineContext().ensureActive()
                    }
                    logger.error(e) { "Failed to write message to storage" }
                }
            }.launchIn(this)
        }


        propertyDescriptors.filter { it.readable }.forEach { descriptor ->
            val storageMeta = Laminate(
                descriptor.metadata,
                meta,
                storageConfig
            )[MessageStorageScheme.MESSAGE_STORAGE_KEY]

            if (storageMeta != null && !storageMeta.isEmpty()) {
                val storageConfiguration = MessageStorageScheme.read(storageMeta)
                val storageName = storageConfiguration.storage
                val storage = storageMap[storageName] ?: error("Storage with name $storageName is not defined")

                propertyMessageFlow(descriptor.name).filterPropertyChanges(storageConfiguration.sampleInterval).onEach { message ->
                    try {
                        storage.write(message.changeSource { deviceName + it })
                    } catch (e: Exception) {
                        if (e is CancellationException) {
                            currentCoroutineContext().ensureActive()
                        }
                        logger.error(e) { "Failed to write message to storage" }
                    }
                }.launchIn(this)

            }
        }
    }

    fun DeviceTree.storeDeviceProperties(deviceName: Name) {
        device?.storeDeviceProperties(deviceName)
        children.forEach {(childName, childTree)->
            childTree.storeDeviceProperties(deviceName + childName)
        }
    }

    storeDeviceProperties(Name.EMPTY)

}