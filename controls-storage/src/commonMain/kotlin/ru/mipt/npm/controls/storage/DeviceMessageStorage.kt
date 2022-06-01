package ru.mipt.npm.controls.storage

import kotlinx.datetime.Instant
import ru.mipt.npm.controls.api.DeviceMessage
import space.kscience.dataforge.names.Name

/**
 * A storage for Controls-kt [DeviceMessage]
 */
public interface DeviceMessageStorage {
    public suspend fun write(event: DeviceMessage)

    public suspend fun readAll(): List<DeviceMessage>

    public suspend fun read(
        eventType: String,
        range: ClosedRange<Instant>? = null,
        sourceDevice: Name? = null,
        targetDevice: Name? = null,
    ): List<DeviceMessage>

    public fun close()
}