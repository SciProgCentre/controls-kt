package ru.mipt.npm.xodus.serialization.json

import jetbrains.exodus.entitystore.Entity
import jetbrains.exodus.entitystore.EntityId
import jetbrains.exodus.entitystore.PersistentEntityStore
import jetbrains.exodus.entitystore.StoreTransaction
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

internal fun StoreTransaction.encodeToEntity(jsonElement: JsonElement, entity: Entity) {
    when (jsonElement) {
        is JsonPrimitive -> throw IllegalStateException("Can't serialize primitive value to entity")
        is JsonArray -> throw IllegalStateException("Can't serialize array value to entity")
        is JsonObject -> {
            jsonElement.forEach { entry ->
                entry.value.let { value ->
                    when(value) {
                        // не сможем десериализовать, если JsonNull (надо ли обрабатывать???) (можно сохранить в отдельный список ключи null-ов)
                        is JsonPrimitive -> {
                            if (value.isString) {
                                entity.setProperty(entry.key, value.content)
                            } else {
                                (value.longOrNull ?: value.doubleOrNull ?: value.booleanOrNull)?.let {
                                    entity.setProperty(
                                        entry.key,
                                        it
                                    )
                                }
                            }
                        }

                        // считаем, что все элементы массива - JsonObject, иначе не можем напрямую сериализовать (надо придывать костыли???)
                        // не сможем десериализовать, если массив пустой (надо ли обрабатывать???) (можно сохранять в отдельный список ключи пустых массивов)
                        is JsonArray -> {
                            value.forEach { element ->
                                val childEntity = newEntity("${entity.type}.${entry.key}")
                                encodeToEntity(element, childEntity)
                                entity.addLink(entry.key, childEntity)
                            }
                        }

                        is JsonObject -> {
                            val childEntity = newEntity("${entity.type}.${entry.key}")
                            encodeToEntity(value, childEntity)
                            entity.setLink(entry.key, childEntity)
                        }
                    }
                }
            }
        }
    }
}

public fun <T> StoreTransaction.encodeToEntity(serializer: SerializationStrategy<T>, value: T, entityType: String): Entity {
    val entity: Entity = newEntity(entityType)
    encodeToEntity(Json.encodeToJsonElement(serializer, value), entity)
    return entity
}

public inline fun <reified T> StoreTransaction.encodeToEntity(value: T, entityType: String): Entity =
    encodeToEntity(serializer(), value, entityType)

public fun <T> PersistentEntityStore.encodeToEntity(serializer: SerializationStrategy<T>, value: T, entityType: String): EntityId {
    return computeInTransaction { txn ->
        txn.encodeToEntity(serializer, value, entityType).id
    }
}

public inline fun <reified T> PersistentEntityStore.encodeToEntity(value: T, entityType: String): EntityId =
    encodeToEntity(serializer(), value, entityType)

@OptIn(InternalSerializationApi::class)
public fun <T : Any> PersistentEntityStore.encodeToEntity(value: T, entityType: String, serializer: KSerializer<T>): EntityId =
    encodeToEntity(serializer, value, entityType)
