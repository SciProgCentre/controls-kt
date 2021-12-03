package ru.mipt.npm.controls.xodus

import io.ktor.application.*
import jetbrains.exodus.entitystore.PersistentEntityStore
import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.hubMessageFlow
import ru.mipt.npm.magix.server.GenericMagixMessage
import ru.mipt.npm.xodus.serialization.json.encodeToEntity
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import java.nio.file.Paths

internal object DefaultXodusConfig {
    val entityStorePath = Paths.get(".messages")
}

public object EntityStoreFactory : Factory<PersistentEntityStore> {
    override fun invoke(meta: Meta, context: Context): PersistentEntityStore {
        return PersistentEntityStores.newInstance(
            meta["xodusConfig"]?.get("entityStorePath")?.string ?: DefaultXodusConfig.entityStorePath.toString()
        )
    }
}

@OptIn(InternalCoroutinesApi::class)
public fun DeviceManager.connectXodus(
    factory: Factory<PersistentEntityStore>,
    filterCondition: suspend (DeviceMessage) -> Boolean  = { true }
): Job {
    val entityStore = factory.invoke(meta, context)
    logger.debug { "Device entity store opened" }

    return hubMessageFlow(context).filter(filterCondition).onEach { message ->
        entityStore.encodeToEntity(message, "DeviceMessage")
    }.launchIn(context).apply {
        invokeOnCompletion(onCancelling = true) {
            entityStore.close()
            logger.debug { "Device entity store closed" }
        }
    }
}

//public fun CoroutineScope.startMagixServer(
//    entityStore: PersistentEntityStore,
//    flowFilter: suspend (GenericMagixMessage) -> Boolean = { true },
//    port: Int = MagixEndpoint.DEFAULT_MAGIX_HTTP_PORT,
//    buffer: Int = 100,
//    enableRawRSocket: Boolean = true,
//    enableZmq: Boolean = true,
//    applicationConfiguration: Application.(MutableSharedFlow<GenericMagixMessage>) -> Unit = {},
//): ApplicationEngine = startMagixServer(
//    port, buffer, enableRawRSocket, enableZmq
//) { flow ->
//    applicationConfiguration(flow)
//    flow.filter(flowFilter).onEach { message ->
//        entityStore.executeInTransaction { txn ->
//            val entity = txn.newEntity("MagixMessage")
//            entity.setProperty("value", message.toString())
//        }
//    }
//}

public fun SharedFlow<GenericMagixMessage>.storeInXodus(
    entityStore: PersistentEntityStore,
    flowFilter: suspend (GenericMagixMessage) -> Boolean = { true },
){
    filter(flowFilter).onEach { message ->
        entityStore.encodeToEntity(message, "MagixMessage")
    }
}

@OptIn(InternalCoroutinesApi::class)
public fun Application.storeInXodus(
    factory: Factory<PersistentEntityStore>,
    flow: MutableSharedFlow<GenericMagixMessage>,
    meta: Meta = Meta.EMPTY
) {
    val entityStore = factory.invoke(meta)

    flow.storeInXodus(entityStore)
    coroutineContext.job.invokeOnCompletion(onCancelling = true) {
        entityStore.close()
    }
}
