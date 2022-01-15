package ru.mipt.npm.controls.storage.asynchronous

import io.ktor.application.*
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import ru.mipt.npm.controls.storage.synchronous.StorageKind
import ru.mipt.npm.magix.server.GenericMagixMessage
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta

/**
 * Asynchronous version of synchronous API, so for more details check relative docs
 */

internal fun Flow<GenericMagixMessage>.store(
    client: AsynchronousStorageClient,
    flowFilter: suspend (GenericMagixMessage) -> Boolean = { true },
) {
    filter(flowFilter).onEach { message ->
        client.storeValue(message, StorageKind.MAGIX_SERVER)
    }
}

@OptIn(InternalCoroutinesApi::class)
public fun Application.store(
    flow: MutableSharedFlow<GenericMagixMessage>,
    factory: Factory<AsynchronousStorageClient>,
    meta: Meta = Meta.EMPTY,
    flowFilter: suspend (GenericMagixMessage) -> Boolean = { true },
) {
    val client = factory(meta)

    flow.store(client, flowFilter)
    coroutineContext.job.invokeOnCompletion(onCancelling = true) {
        client.close()
    }
}
