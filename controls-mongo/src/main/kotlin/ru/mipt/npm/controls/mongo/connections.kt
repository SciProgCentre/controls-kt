package ru.mipt.npm.controls.mongo

import io.ktor.application.*
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.coroutine.insertOne
import org.litote.kmongo.reactivestreams.KMongo
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.hubMessageFlow
import ru.mipt.npm.magix.server.GenericMagixMessage
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name

private const val DEFAULT_MONGO_DATABASE_URL = "mongodb://mongoadmin:secret@localhost:27888"
private const val DEFAULT_DEVICE_MESSAGE_DATABASE_NAME = "deviceMessage"
private const val DEFAULT_MAGIX_MESSAGE_DATABASE_NAME = "magixMessage"
public val MONGO_DATABASE_URL_PROPERTY: Name = Name.of("mongo", "databaseUrl")
public val MONGO_DEVICE_MESSAGE_DATABASE_NAME_PROPERTY: Name = Name.of("mongo", "deviceMessageDatabaseName")
public val MONGO_MAGIX_MESSAGE_DATABASE_NAME_PROPERTY: Name = Name.of("mongo", "magixMessageDatabaseName")

public object DefaultMongoClientFactory : Factory<CoroutineClient> {
    override fun invoke(meta: Meta, context: Context): CoroutineClient = meta[MONGO_DATABASE_URL_PROPERTY]?.string?.let {
        KMongo.createClient(it).coroutine
    } ?: KMongo.createClient(DEFAULT_MONGO_DATABASE_URL).coroutine
}

@OptIn(InternalCoroutinesApi::class)
public fun DeviceManager.connectMongo(
    factory: Factory<CoroutineClient>,
    filterCondition: suspend (DeviceMessage) -> Boolean  = { true }
): Job {
    val client = factory.invoke(meta, context)
    logger.debug { "Mongo client opened" }
    val collection = client
        .getDatabase(meta[MONGO_DEVICE_MESSAGE_DATABASE_NAME_PROPERTY]?.string ?: DEFAULT_DEVICE_MESSAGE_DATABASE_NAME)
        .getCollection<DeviceMessage>()
    return hubMessageFlow(context).filter(filterCondition).onEach { message ->
        context.launch {
                collection.insertOne(Json.encodeToString(message))
        }
    }.launchIn(context).apply {
        invokeOnCompletion(onCancelling = true) {
            logger.debug { "Mongo client closed" }
            client.close()
        }
    }
}

internal fun Flow<GenericMagixMessage>.storeInMongo(
    collection: CoroutineCollection<GenericMagixMessage>,
    flowFilter: suspend (GenericMagixMessage) -> Boolean = { true },
) {
    filter(flowFilter).onEach { message ->
        collection.insertOne(Json.encodeToString(message))
    }
}

@OptIn(InternalCoroutinesApi::class)
public fun Application.storeInMongo(
    flow: MutableSharedFlow<GenericMagixMessage>,
    meta: Meta = Meta.EMPTY,
    factory: Factory<CoroutineClient> = DefaultMongoClientFactory,
    flowFilter: suspend (GenericMagixMessage) -> Boolean = { true },
) {
    val client = factory.invoke(meta)
    val collection = client
        .getDatabase(meta[MONGO_MAGIX_MESSAGE_DATABASE_NAME_PROPERTY]?.string ?: DEFAULT_MAGIX_MESSAGE_DATABASE_NAME)
        .getCollection<GenericMagixMessage>()

    flow.storeInMongo(collection, flowFilter)
    coroutineContext.job.invokeOnCompletion(onCancelling = true) {
        client.close()
    }
}
