package space.kscience.controls.storage

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.*
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.lastOrNull
import space.kscience.dataforge.names.plus
import kotlin.time.Duration

public class ControlsStoragePlugin : AbstractPlugin() {
    public val io: IOPlugin by require(IOPlugin)

    override val tag: PluginTag get() = Companion.tag

    public val rowEnvelopeConverters: Map<String, RowsEnvelopeConverter<Meta>> by lazy {
        context.gather<RowsEnvelopeConverter<Meta>>(RowsEnvelopeConverter.ROWS_ENVELOPE_CONVERTER_TARGET).values.associateBy { it.envelopeType }
    }

    public val messageStorageFactories: Map<Name, Factory<DeviceMessageStorage>> by lazy {
        context.gather<Factory<DeviceMessageStorage>>(MESSAGE_STORAGE_FACTORY_TARGET)
    }

    private fun findMessageStorageByType(type: String): Factory<DeviceMessageStorage>? {
        //return full qualified name match immediately
        messageStorageFactories[name]?.let { return it }

        val matches = messageStorageFactories.entries.filter {
            it.key.lastOrNull()?.toString() == type
        }

        return when (matches.size) {
            0 -> null
            1 -> matches.first().value
            else -> error("Multiple storage factories found for $type: ${matches.map { it.key }}")
        }
    }

    /**
     * Build a message storage from a meta description
     */
    public fun buildMessageStorage(storageMeta: Meta): DeviceMessageStorage {
        val storageType = storageMeta["type"].string ?: error("No storage type in $storageMeta")
        val factory = findMessageStorageByType(storageType) ?: error("No storage factory for $storageType")
        return factory.build(context, storageMeta)
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
private fun Flow<ValueWithTime<Meta>>.filterPropertyChanges(interval: Duration?): Flow<ValueWithTime<Meta>> =
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
 * @return the launched coroutine [Job] responsible for configuring and running storage subscriptions for this
 * device tree.
 */
public fun CoroutineScope.launchStorageJob(
    deviceTree: DeviceTree,
    storageMap: Map<String, WriteableDeviceMessageStorage>,
): Job = launch {

    fun Device.storeDeviceProperties(deviceName: Name) {

        val deviceStorageMeta = attributes[MessageStorageScheme.MESSAGE_STORAGE_KEY]

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
            val storageMeta = Laminate(descriptor.metadata, attributes)[MessageStorageScheme.MESSAGE_STORAGE_KEY]

            if (storageMeta != null && !storageMeta.isEmpty()) {
                val storageConfiguration = MessageStorageScheme.read(storageMeta)
                val storageName = storageConfiguration.storage
                val storage = storageMap[storageName] ?: error("Storage with name $storageName is not defined")

                subscribeToProperty(descriptor.name).filterPropertyChanges(storageConfiguration.sampleInterval)
                    .onEach { (value, time) ->
                        try {
                            val message = PropertyChangedMessage(
                                time = time,
                                property = descriptor.name,
                                value = value,
                                sourceDevice = deviceName
                            )
                            storage.write(message)
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
        children.forEach { (childName, childTree) ->
            childTree.storeDeviceProperties(deviceName + childName)
        }
    }

    deviceTree.storeDeviceProperties(Name.EMPTY)
}

/**
 * Launches a storage job for the given device tree using writable message storages declared in metadata.
 *
 * The function resolves [ControlsStoragePlugin] from this context, reads storage definitions from
 * [storageMeta] under [MessageStorageScheme.MESSAGE_STORAGE_KEY], builds each configured message storage,
 * verifies that every produced storage is writable, and delegates to the storage job launcher using the
 * resulting storage map.
 *
 * The function fails if the metadata does not contain storage definitions, or if any configured storage
 * cannot be built as a [WriteableDeviceMessageStorage].
 *
 * @param deviceTree device tree whose device messages should be collected and written to configured storages.
 * @param storageMeta metadata containing named message storage definitions.
 * @return the launched coroutine [Job] that manages message storage subscriptions for the device tree.
 */
public fun Context.launchStorageJob(
    deviceTree: DeviceTree,
    storageMeta: Meta
): Job {
    val storagePlugin = request(ControlsStoragePlugin)
    val storageMap = storageMeta[MessageStorageScheme.MESSAGE_STORAGE_KEY]?.items
        ?.map { (key, storageConfig) ->
            val storage = storagePlugin.buildMessageStorage(storageConfig) as? WriteableDeviceMessageStorage
                ?: error("Storage produced with $storageConfig is not writeable")
            key.toString() to storage
        }?.toMap() ?: error("No storage definition in $storageMeta")

    return launchStorageJob(deviceTree, storageMap)
}
