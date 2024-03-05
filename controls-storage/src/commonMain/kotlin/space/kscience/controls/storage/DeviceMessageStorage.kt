package space.kscience.controls.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.serialDescriptor
import space.kscience.controls.api.DeviceMessage
import space.kscience.dataforge.names.Name

/**
 * A storage for Controls-kt [DeviceMessage]
 */
public interface DeviceMessageStorage {
    public suspend fun write(event: DeviceMessage)

    /**
     * Return all messages in a storage as a flow
     */
    public fun readAll(): Flow<DeviceMessage>

    /**
     * Flow messages with given [eventType] and filters by [range], [sourceDevice] and [targetDevice].
     * Null in filters means that there is not filtering for this field.
     */
    public fun read(
        eventType: String,
        range: ClosedRange<Instant>? = null,
        sourceDevice: Name? = null,
        targetDevice: Name? = null,
    ): Flow<DeviceMessage>

    public fun close()
}

/**
 * Query all messages of given type
 */
@OptIn(ExperimentalSerializationApi::class)
public inline fun <reified T : DeviceMessage> DeviceMessageStorage.read(
    range: ClosedRange<Instant>? = null,
    sourceDevice: Name? = null,
    targetDevice: Name? = null,
): Flow<T> = read(serialDescriptor<T>().serialName, range, sourceDevice, targetDevice).map {
    //Check that all types are correct
    it as T
}