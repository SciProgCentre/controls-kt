package space.kscience.controls.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import kotlin.time.Instant

/**
 * A storage for Controls-kt [DeviceMessage]
 */
public interface DeviceMessageStorage {
    /**
     * Write a single message to the storage
     */
    public suspend fun write(event: DeviceMessage)

    /**
     * Write several messages in the same transaction to the database
     */
    public suspend fun writeAll(events: Iterable<DeviceMessage>): Unit = events.forEach { write(it) }

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
 * Query all messages of a given type
 */
@OptIn(ExperimentalSerializationApi::class)
public inline fun <reified T : DeviceMessage> DeviceMessageStorage.read(
    range: ClosedRange<Instant>? = null,
    sourceDevice: Name? = null,
    targetDevice: Name? = null,
): Flow<T> = read(DeviceMessage.serialNameFor<T>(), range, sourceDevice, targetDevice).map {
    //Check that all types are correct
    it as T
}

public fun <T> DeviceMessageStorage.propertyHistory(
    propertyName: String,
    converter: MetaConverter<T>,
): ValueHistory<T> = object : ValueHistory<T> {
    override fun flowHistory(from: Instant, until: Instant): Flow<ValueWithTime<T>> =
        read<PropertyChangedMessage>(from..until)
            .filter { it.property == propertyName }
            .map { ValueWithTime(converter.read(it.value), it.time) }
}