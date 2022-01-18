package ru.mipt.npm.controls.xodus

import jetbrains.exodus.entitystore.PersistentEntityStore
import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.storage.EventStorage
import ru.mipt.npm.xodus.serialization.json.decodeFromEntity
import ru.mipt.npm.xodus.serialization.json.encodeToEntity
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name

private const val DEFAULT_XODUS_STORE_PATH = ".storage"
public val XODUS_STORE_PROPERTY: Name = Name.of("xodus", "entityStorePath")

private const val DEVICE_HUB_ENTITY_TYPE = "DeviceMessage"
private const val MAGIX_SERVER_ENTITY_TYPE = "MagixMessage"

public class XodusEventStorage(private val entityStore: PersistentEntityStore) : EventStorage {
    override suspend fun <T : Any> storeDeviceMessage(value: T, serializer: KSerializer<T>) {
        entityStore.encodeToEntity(value, DEVICE_HUB_ENTITY_TYPE, serializer)
    }

    override suspend fun <T : Any> storeMagixMessage(value: T, serializer: KSerializer<T>) {
        entityStore.encodeToEntity(value, MAGIX_SERVER_ENTITY_TYPE, serializer)
    }

    override suspend fun getPropertyHistory(
        sourceDeviceName: String,
        propertyName: String,
    ): List<PropertyChangedMessage> = entityStore.computeInTransaction { txn ->
        txn.find(DEVICE_HUB_ENTITY_TYPE, "type", "property.changed")
            .filter {
                it?.getProperty("sourceDevice") == sourceDeviceName && it.getProperty("property") == propertyName
            }
            .sortedByDescending { it?.getProperty("time")?.let { timeStr -> Instant.parse(timeStr as String) } }
            .map { txn.decodeFromEntity(it, PropertyChangedMessage.serializer()) }
            .toList()
    }

    override fun close() {
        entityStore.close()
    }

    public companion object : Factory<EventStorage> {
        override fun invoke(meta: Meta, context: Context): EventStorage {
            val entityStore = context.getPersistentEntityStore(meta)
            return XodusEventStorage(entityStore)
        }
    }
}

private fun Context.getPersistentEntityStore(meta: Meta = Meta.EMPTY): PersistentEntityStore {
    val storePath = meta[XODUS_STORE_PROPERTY]?.string
        ?: properties[XODUS_STORE_PROPERTY]?.string
        ?: DEFAULT_XODUS_STORE_PATH

    return PersistentEntityStores.newInstance(storePath)
}
