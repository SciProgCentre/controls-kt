package space.kscience.controls.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import space.kscience.controls.api.DeviceMessage
import space.kscience.dataforge.names.Name
import kotlin.time.Instant

/**
 * Routes device messages between multiple labeled storages.
 *
 * Read operations are delegated to the storages selected by the read selector and their resulting flows are merged.
 * Write operations are delegated to the storage label returned by the write selector. If the write selector returns
 * null, the message is ignored. The selected storage must exist in the storage map and must implement
 * [WriteableDeviceMessageStorage].
 *
 * Closing this router closes all storages contained in the storage map.
 *
 * @param storageMap mapping of storage labels to storages available to this router.
 * @param storageWriteSelector selects the storage label used for writing a message, or returns null to skip writing.
 * @param storageReadSelector selects storage labels used for reading messages for an optional event type and source device.
 *
 * @throws IllegalStateException when a selected storage label is not present in the storage map.
 * @throws IllegalStateException when a selected write storage does not support writing.
 */
public class DeviceMessageRouter(
    private val storageMap: Map<String, DeviceMessageStorage>,
    private val storageWriteSelector: (DeviceMessage) -> String?,
    private val storageReadSelector: (eventType: String?, sourceDevice: Name?) -> Collection<String> = { _, _ -> storageMap.keys }
) : WriteableDeviceMessageStorage {

    override fun read(
        range: ClosedRange<Instant>?,
        sourceDevice: Name?,
        targetDevice: Name?
    ): Flow<DeviceMessage> = storageReadSelector(null, sourceDevice).map {
        storageMap[it]?.read(range, sourceDevice, targetDevice) ?: error("No storage defined for label $it")
    }.merge()

    override fun read(
        eventType: String,
        range: ClosedRange<Instant>?,
        sourceDevice: Name?,
        targetDevice: Name?
    ): Flow<DeviceMessage> = storageReadSelector(eventType, sourceDevice).map {
        storageMap[it]?.read(range, sourceDevice, targetDevice) ?: error("No storage defined for label $it")
    }.merge()

    override suspend fun write(event: DeviceMessage) {
        val storageLabel = storageWriteSelector(event)?: return
        val storage = storageMap[storageLabel] ?: error("No storage defined for label $storageLabel")
        if(storage is WriteableDeviceMessageStorage){
            storage.write(event)
        } else {
            error("Storage $storageLabel is not writeable")
        }
    }

    override fun close() {
        storageMap.values.forEach {
            it.close()
        }
    }

}