package ru.mipt.npm.controls.mongo

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.coroutine.insertOne
import org.litote.kmongo.reactivestreams.KMongo
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.hubMessageFlow
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string

internal object DefaultMongoConfig {
    const val databaseName = "deviceMessage"
}

public object MongoClientFactory : Factory<CoroutineClient> {
    private const val connectionString: String = "mongodb://mongoadmin:secret@localhost:27888"

    override fun invoke(meta: Meta, context: Context): CoroutineClient = meta["mongoConfig"]?.get("connectionString")?.string?.let {
        KMongo.createClient(it).coroutine
    } ?: KMongo.createClient(connectionString).coroutine
}

@OptIn(InternalCoroutinesApi::class)
public fun DeviceManager.connectMongo(
    factory: Factory<CoroutineClient>,
    filterCondition: suspend (DeviceMessage) -> Boolean  = { true }
): Job {
    val client = factory.invoke(meta, context)
    logger.debug { "Mongo client opened" }
    val collection = client
        .getDatabase(meta["mongoConfig"]?.get("databaseName")?.string ?: DefaultMongoConfig.databaseName)
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
