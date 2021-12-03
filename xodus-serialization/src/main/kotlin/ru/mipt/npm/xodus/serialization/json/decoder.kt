package ru.mipt.npm.xodus.serialization.json

import jetbrains.exodus.entitystore.Entity
import jetbrains.exodus.entitystore.StoreTransaction
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer

internal fun StoreTransaction.decodeFromEntity(entity: Entity): JsonElement = buildJsonObject {
    entity.propertyNames.forEach { property ->
        entity.getProperty(property).let { value ->
            when (value) {
                is Number -> put(property, value)
                is Boolean -> put(property, value)
                is String -> put(property, value)
                else -> throw IllegalStateException("Unsupported type for primitive field")
            }
        }
    }

    entity.linkNames.forEach { link ->
        entity.getLinks(link).let { entities ->
            when (entities.size()) {
                1L -> entities.first?.let { put(link, decodeFromEntity(it)) }
                else -> {
                    putJsonArray(link) {
                        entities.forEach {
                            add(decodeFromEntity(it))
                        }
                    }
                }
            }
        }
    }
}

public fun <T> StoreTransaction.decodeFromEntity(entity: Entity, deserializer: DeserializationStrategy<T>): T {
    val jsonElement = decodeFromEntity(entity)
    return Json.decodeFromJsonElement(deserializer, jsonElement)
}

public inline fun <reified T> StoreTransaction.decodeFromEntity(entity: Entity): T = decodeFromEntity(entity, serializer())
