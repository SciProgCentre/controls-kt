package ru.mipt.npm.controls.xodus

import jetbrains.exodus.entitystore.PersistentEntityStore
import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.storage.synchronous.StorageKind
import ru.mipt.npm.controls.storage.synchronous.SynchronousStorageClient
import ru.mipt.npm.xodus.serialization.json.decodeFromEntity
import ru.mipt.npm.xodus.serialization.json.encodeToEntity
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import kotlin.reflect.KClass

private const val DEFAULT_XODUS_STORE_PATH = ".storage"
public val XODUS_STORE_PROPERTY: Name = Name.of("xodus", "entityStorePath")

private const val DEVICE_HUB_ENTITY_TYPE = "DeviceMessage"
private const val MAGIX_SERVER_ENTITY_TYPE = "MagixMessage"

internal class SynchronousXodusClient(private val entityStore: PersistentEntityStore) : SynchronousStorageClient {
    override fun <T : Any> storeValueInDeviceHub(value: T, serializer: KSerializer<T>) {
        entityStore.encodeToEntity(value, DEVICE_HUB_ENTITY_TYPE, serializer)
    }

    override fun <T : Any> storeValueInMagixServer(value: T, serializer: KSerializer<T>) {
        entityStore.encodeToEntity(value, MAGIX_SERVER_ENTITY_TYPE, serializer)
    }

    override fun getPropertyHistory(
        sourceDeviceName: String,
        propertyName: String
    ): List<PropertyChangedMessage> {
        return entityStore.computeInTransaction { txn ->
            txn.find(DEVICE_HUB_ENTITY_TYPE, "type", "property.changed").asSequence()
                .filter { it?.getProperty("sourceDevice")?.let { it == sourceDeviceName } ?: false &&
                        it?.getProperty("property")?.let { it == propertyName } ?: false
                }.sortedByDescending { it?.getProperty("time")?.let { timeStr -> Instant.parse(timeStr as String) } }
                .toList().map { txn.decodeFromEntity(it) }
        }
    }

    override fun close() {
        entityStore.close()
    }
}

private fun Context.getPersistentEntityStore(meta: Meta = Meta.EMPTY): PersistentEntityStore {
    val storePath = meta[XODUS_STORE_PROPERTY]?.string
        ?: properties[XODUS_STORE_PROPERTY]?.string
        ?: DEFAULT_XODUS_STORE_PATH

    return PersistentEntityStores.newInstance(storePath)
}

public object DefaultSynchronousXodusClientFactory : Factory<SynchronousStorageClient> {
    override fun invoke(meta: Meta, context: Context): SynchronousStorageClient {
        val entityStore = context.getPersistentEntityStore(meta)
        return SynchronousXodusClient(entityStore)
    }
}
