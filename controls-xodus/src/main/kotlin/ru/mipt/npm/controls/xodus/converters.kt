package ru.mipt.npm.controls.xodus

import jetbrains.exodus.entitystore.Entity
import jetbrains.exodus.entitystore.StoreTransaction
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.magix.api.MagixMessage

public fun PropertyChangedMessage.toEntity(transaction: StoreTransaction): Entity {
    val entity = transaction.newEntity("PropertyChangedMessage")
    entity.setProperty("property", property)
    entity.setProperty("value", value.toString())
    entity.setProperty("sourceDevice", sourceDevice.toString())
    targetDevice?.let { entity.setProperty("targetDevice", it.toString()) }
    comment?.let { entity.setProperty("comment", it) }
    time?.let { entity.setProperty("time", it.toEpochMilliseconds()) }
    return entity
}

public fun MagixMessage<DeviceMessage>.toEntity(transaction: StoreTransaction): Entity {
    val entity = transaction.newEntity("MagixMessage")
    entity.setProperty("format", format)
    entity.setProperty("origin", origin)
    if (payload is PropertyChangedMessage) {
        val payloadEntity = (payload as PropertyChangedMessage).toEntity(transaction)
        entity.setLink("payload", payloadEntity)
    }
    target?.let { entity.setProperty("target", it) }
    id?.let { entity.setProperty("id", it) }
    parentId?.let { entity.setProperty("parentId", it) }
    user?.let { entity.setProperty("user", it.toString()) }
    return entity
}
