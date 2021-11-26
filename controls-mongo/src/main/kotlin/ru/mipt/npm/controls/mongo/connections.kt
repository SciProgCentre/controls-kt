package ru.mipt.npm.controls.mongo

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
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string

public object MongoClientFactory : Factory<CoroutineClient> {
    public const val connectionString: String = "mongodb://mongoadmin:secret@localhost:27888"

    override fun invoke(meta: Meta, context: Context): CoroutineClient {
        return meta["connectionString"]?.string?.let {
            KMongo.createClient(it).coroutine
        } ?: KMongo.createClient(connectionString).coroutine
    }
}

public fun DeviceManager.connectMongo(
    client: CoroutineClient,
    filterCondition: suspend (DeviceMessage) -> Boolean  = { true }
): Job = hubMessageFlow(context).filter(filterCondition).onEach { message ->
    context.launch {
        client
            .getDatabase("deviceServer")
            .getCollection<DeviceMessage>()
            .insertOne(Json.encodeToString(message))
    }
}.launchIn(context)
