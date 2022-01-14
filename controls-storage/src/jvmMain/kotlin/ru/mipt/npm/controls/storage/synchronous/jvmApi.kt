package ru.mipt.npm.controls.storage.synchronous

import io.ktor.application.*
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import ru.mipt.npm.magix.server.GenericMagixMessage
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta

internal fun Flow<GenericMagixMessage>.store(
    client: SynchronousStorageClient,
    flowFilter: suspend (GenericMagixMessage) -> Boolean = { true },
) {
    filter(flowFilter).onEach { message ->
        client.storeValue(message, StorageKind.MAGIX_SERVER)
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
    meta: Meta = Meta.EMPTY,
    factory: Factory<SynchronousStorageClient>,
    flowFilter: suspend (GenericMagixMessage) -> Boolean = { true },
) {
    val client = factory(meta)

    flow.store(client, flowFilter)
    coroutineContext.job.invokeOnCompletion(onCancelling = true) {
        client.close()
    }
}
