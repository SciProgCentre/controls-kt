package space.kscience.controls.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.time.ValueHistory
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import kotlin.time.Instant

/**
 * A storage for Controls-kt [DeviceMessage]
 */
public interface DeviceMessageStorage : AutoCloseable {

    /**
     * Return all messages in a storage as a flow and optionally filters by [range], [sourceDevice] and [targetDevice].
     */
    public fun read(
        range: ClosedRange<Instant>? = null,
        sourceDevice: Name? = null,
        targetDevice: Name? = null,
    ): Flow<DeviceMessage>

    /**
     * Flow messages with given [eventType] and optionally filters by [range], [sourceDevice] and [targetDevice].
     * Null in filters means that there is no filtering for this field.
     */
    public fun read(
        eventType: String,
        range: ClosedRange<Instant>? = null,
        sourceDevice: Name? = null,
        targetDevice: Name? = null,
    ): Flow<DeviceMessage>

    public companion object {
        public fun merge(vararg storage: DeviceMessageStorage): DeviceMessageStorage = object : DeviceMessageStorage {
            override fun read(
                range: ClosedRange<Instant>?,
                sourceDevice: Name?,
                targetDevice: Name?
            ): Flow<DeviceMessage> = storage.map { it.read(range, sourceDevice, targetDevice) }.merge()

            override fun read(
                eventType: String,
                range: ClosedRange<Instant>?,
                sourceDevice: Name?,
                targetDevice: Name?
            ): Flow<DeviceMessage> = storage.map { it.read(eventType, range, sourceDevice, targetDevice) }.merge()

            override fun close() {
                storage.forEach { it.close() }
            }
        }
    }
}

/**
 * An interface that extends [DeviceMessageStorage] and allows writing messages to the storage.
 */
public interface WriteableDeviceMessageStorage : DeviceMessageStorage {
    /**
     * Write a single message to the storage
     */
    public suspend fun write(event: DeviceMessage)

    /**
     * Write several messages in the same transaction to the database
     */
    public suspend fun writeAll(events: Iterable<DeviceMessage>): Unit = events.forEach { write(it) }
}

/**
 * Query all messages of a given type
 */
public inline fun <reified T : DeviceMessage> DeviceMessageStorage.read(
    range: ClosedRange<Instant>? = null,
    sourceDevice: Name? = null,
    targetDevice: Name? = null,
): Flow<T> = read(DeviceMessage.serialNameFor<T>(), range, sourceDevice, targetDevice).map {
    //Check that all types are correct
    it as T
}

/**
 * Retrieves the historical data of a specific property from the device message storage.
 *
 * @param T The type of the property value.
 * @param propertyName The name of the property for which the history is requested.
 * @param converter A MetaConverter used to convert the property values from Meta format.
 * @return A ValueHistory instance that allows access to the history of the specified property.
 */
public fun <T> DeviceMessageStorage.propertyHistory(
    propertyName: String,
    converter: MetaConverter<T>,
    deviceName: Name? = null,
): ValueHistory<T> = object : ValueHistory<T> {
    override fun flow(from: Instant, until: Instant): Flow<ValueWithTime<T>> =
        read<PropertyChangedMessage>(from..until)
            .filter {
                it.property == propertyName && (deviceName == null || it.sourceDevice == deviceName)
            }
            .map { ValueWithTime(converter.read(it.value), it.time) }
}