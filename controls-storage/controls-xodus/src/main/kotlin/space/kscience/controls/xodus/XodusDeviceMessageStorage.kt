package space.kscience.controls.xodus

import jetbrains.exodus.entitystore.Entity
import jetbrains.exodus.entitystore.PersistentEntityStore
import jetbrains.exodus.entitystore.PersistentEntityStores
import jetbrains.exodus.entitystore.StoreTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.storage.DeviceMessageStorage
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.io.workDirectory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.matches
import space.kscience.dataforge.names.parseAsName


internal fun StoreTransaction.writeMessage(message: DeviceMessage): Unit {
    val entity: Entity = newEntity(XodusDeviceMessageStorage.DEVICE_MESSAGE_ENTITY_TYPE)
    val json = Json.encodeToJsonElement(DeviceMessage.serializer(), message).jsonObject
    val type = json["type"]?.jsonPrimitive?.content ?: error("Message json representation must have type.")
    entity.setProperty("type", type)

    message.sourceDevice?.let {
        entity.setProperty(DeviceMessage::sourceDevice.name, it.toString())
    }
    message.targetDevice?.let {
        entity.setProperty(DeviceMessage::targetDevice.name, it.toString())
    }
    entity.setProperty(DeviceMessage::targetDevice.name, message.time.toString())
    entity.setBlobString("json", Json.encodeToString(json))
}


@OptIn(DFExperimental::class)
private fun Entity.propertyMatchesName(propertyName: String, pattern: Name? = null) =
    pattern == null || getProperty(propertyName).toString().parseAsName().matches(pattern)

private fun Entity.timeInRange(range: ClosedRange<Instant>?): Boolean {
    if (range == null) return true
    val time: Instant? = getProperty(DeviceMessage::time.name)?.let { entityString ->
        Instant.parse(entityString.toString())
    }
    return time != null && time in range
}

public class XodusDeviceMessageStorage(
    private val entityStore: PersistentEntityStore,
) : DeviceMessageStorage, AutoCloseable {

    override suspend fun write(event: DeviceMessage) {
        entityStore.executeInTransaction { txn ->
            txn.writeMessage(event)
        }
    }

    override fun readAll(): Flow<DeviceMessage> = entityStore.computeInReadonlyTransaction { transaction ->
        transaction.sort(
            DEVICE_MESSAGE_ENTITY_TYPE,
            DeviceMessage::time.name,
            true
        ).map {
            Json.decodeFromString(
                DeviceMessage.serializer(),
                it.getBlobString("json") ?: error("No json content found")
            )
        }
    }.asFlow()

    override fun read(
        eventType: String,
        range: ClosedRange<Instant>?,
        sourceDevice: Name?,
        targetDevice: Name?,
    ): Flow<DeviceMessage> = entityStore.computeInReadonlyTransaction { transaction ->
        transaction.find(
            DEVICE_MESSAGE_ENTITY_TYPE,
            "type",
            eventType
        ).filter {
            it.timeInRange(range) &&
                    it.propertyMatchesName(DeviceMessage::sourceDevice.name, sourceDevice) &&
                    it.propertyMatchesName(DeviceMessage::targetDevice.name, targetDevice)
        }.map {
            Json.decodeFromString(
                DeviceMessage.serializer(),
                it.getBlobString("json") ?: error("No json content found")
            )
        }
    }.asFlow()

    override fun close() {
        entityStore.close()
    }

    public companion object : Factory<XodusDeviceMessageStorage> {
        internal const val DEVICE_MESSAGE_ENTITY_TYPE = "controls-kt.message"
        public val XODUS_STORE_PROPERTY: Name = Name.of("xodus", "storagePath")

        override fun build(context: Context, meta: Meta): XodusDeviceMessageStorage {
            val io = context.request(IOPlugin)
            val storePath = io.workDirectory.resolve(
                meta[XODUS_STORE_PROPERTY]?.string
                    ?: context.properties[XODUS_STORE_PROPERTY]?.string ?: "storage"
            )

            val entityStore = PersistentEntityStores.newInstance(storePath.toFile())

            return XodusDeviceMessageStorage(entityStore)
        }
    }
}