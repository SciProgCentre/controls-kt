package ru.mipt.npm.controls.storage

import io.ktor.application.Application
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import ru.mipt.npm.magix.server.GenericMagixMessage
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta

/**
 * Asynchronous version of synchronous API, so for more details check relative docs
 */

internal fun Flow<GenericMagixMessage>.store(
    client: EventStorage,
    flowFilter: suspend (GenericMagixMessage) -> Boolean = { true },
) {
    filter(flowFilter).onEach { message ->
        client.storeMagixMessage(message)
    }
}

/** Begin to store MagixMessages from certain flow
 * @param flow flow of messages which we will store
 * @param meta Meta which may have some configuration parameters for our storage and will be used in invoke method of factory
 * @param factory factory that will be used for creating persistent entity store instance. DefaultPersistentStoreFactory by default.
 * @param flowFilter allow you to specify messages which we want to store. Always true by default.
 */
@OptIn(InternalCoroutinesApi::class)
public fun Application.store(
    flow: MutableSharedFlow<GenericMagixMessage>,
    factory: Factory<EventStorage>,
    meta: Meta = Meta.EMPTY,
    flowFilter: suspend (GenericMagixMessage) -> Boolean = { true },
) {
    val client = factory(meta)

    flow.store(client, flowFilter)
    coroutineContext.job.invokeOnCompletion(onCancelling = true) {
        client.close()
    }
}
