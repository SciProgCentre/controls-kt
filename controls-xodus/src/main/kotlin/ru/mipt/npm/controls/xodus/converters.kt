package ru.mipt.npm.controls.xodus

import jetbrains.exodus.entitystore.Entity
import jetbrains.exodus.entitystore.StoreTransaction
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.magix.api.MagixMessage
import space.kscience.dataforge.meta.MetaSerializer
import space.kscience.dataforge.meta.isLeaf
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.values.Value
import space.kscience.dataforge.values.ValueType

internal fun PropertyChangedMessage.toEntity(transaction: StoreTransaction): Entity {
    val entity = transaction.newEntity("PropertyChangedMessage")
    entity.setProperty("property", property)
    entity.setProperty("value", value.toString())
    entity.setProperty("sourceDevice", sourceDevice.toString())
    targetDevice?.let { entity.setProperty("targetDevice", it.toString()) }
    comment?.let { entity.setProperty("comment", it) }
    time?.let { entity.setProperty("time", it.toEpochMilliseconds()) }
    return entity
}

internal fun Entity.toPropertyChangedMessage(): PropertyChangedMessage? {
    if (getProperty("property") == null || getProperty("value") == null || getProperty("sourceDevice") == null) {
        return null
    }

    return PropertyChangedMessage(
        getProperty("property") as String,
        Json.decodeFromString(MetaSerializer, getProperty("value") as String),
        Name.parse(getProperty("sourceDevice") as String),
        getProperty("targetDevice")?.let { Name.parse(it as String) },
        getProperty("comment")?.let { it as String },
        getProperty("time")?.let { Instant.fromEpochMilliseconds(it as Long) }
    )
}

internal fun <T> MagixMessage<T>.toEntity(transaction: StoreTransaction): Entity {
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

internal fun Entity.toMagixMessage(): MagixMessage<PropertyChangedMessage>? {
    if (getProperty("format") == null || getProperty("origin") == null) {
        return null
    }

    return getLink("payload")?.toPropertyChangedMessage()?.let { propertyChangedMessage ->
        MagixMessage(
            getProperty("format") as String,
            getProperty("origin") as String,
            propertyChangedMessage,
            getProperty("target")?.let { it as String },
            getProperty("id")?.let { it as String },
            getProperty("parentId")?.let { it as String },
            getProperty("user")?.let { Json.decodeFromString(JsonElement.serializer(), it as String) }
        )
    }
}
