package space.kscience.controls.mongo

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.coroutine.insertOne
import org.litote.kmongo.reactivestreams.KMongo
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.storage.EventStorage
import ru.mipt.npm.magix.server.GenericMagixMessage
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name

private const val DEFAULT_DEVICE_MESSAGE_DATABASE_NAME: String = "deviceMessage"
private const val DEFAULT_MAGIX_MESSAGE_DATABASE_NAME = "magixMessage"
private const val DEFAULT_MONGO_DATABASE_URL = "mongodb://mongoadmin:secret@localhost:27888"
private val MONGO_DEVICE_MESSAGE_DATABASE_NAME_PROPERTY: Name = Name.of("mongo", "deviceMessageDatabaseName")
public val MONGO_MAGIX_MESSAGE_DATABASE_NAME_PROPERTY: Name = Name.of("mongo", "magixMessageDatabaseName")
public val MONGO_DATABASE_URL_PROPERTY: Name = Name.of("mongo", "databaseUrl")

internal class MongoEventStorage(
    private val client: CoroutineClient,
    private val meta: Meta = Meta.EMPTY,
) : EventStorage {
    override suspend fun <T : Any> storeDeviceMessage(value: T, serializer: KSerializer<T>) {
        val collection = client
            .getDatabase(
                meta[MONGO_DEVICE_MESSAGE_DATABASE_NAME_PROPERTY]?.string
                    ?: DEFAULT_DEVICE_MESSAGE_DATABASE_NAME
            )
            .getCollection<DeviceMessage>()

        collection.insertOne(Json.encodeToString(serializer, value))
    }

    override suspend fun <T : Any> storeMagixMessage(value: T, serializer: KSerializer<T>) {
        val collection = client
            .getDatabase(meta[MONGO_MAGIX_MESSAGE_DATABASE_NAME_PROPERTY]?.string
                ?: DEFAULT_MAGIX_MESSAGE_DATABASE_NAME)
            .getCollection<GenericMagixMessage>()

        collection.insertOne(Json.encodeToString(serializer, value))
    }

    override suspend fun getPropertyHistory(
        sourceDeviceName: String,
        propertyName: String,
    ): List<PropertyChangedMessage> {
        TODO("Not yet implemented: problems with deserialization")
    }

    override fun close() {
        client.close()
    }
}

public object DefaultAsynchronousMongoClientFactory : Factory<EventStorage> {
    override fun invoke(meta: Meta, context: Context): EventStorage {
        val client = meta[MONGO_DATABASE_URL_PROPERTY]?.string?.let {
            KMongo.createClient(it).coroutine
        } ?: KMongo.createClient(DEFAULT_MONGO_DATABASE_URL).coroutine

        return MongoEventStorage(client, meta)
    }
}
