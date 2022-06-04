package ru.mipt.npm.magix.storage.xodus

import jetbrains.exodus.entitystore.PersistentEntityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter

public class XodusMagixStorage(
    scope: CoroutineScope,
    private val store: PersistentEntityStore,
    endpoint: MagixEndpoint,
    filter: MagixMessageFilter = MagixMessageFilter(),
) : AutoCloseable {

    //TODO consider message buffering
    private val subscriptionJob = endpoint.subscribe(filter).onEach { message ->
        store.executeInTransaction { transaction ->
            transaction.newEntity(MAGIC_MESSAGE_ENTITY_TYPE).apply {
                setProperty(MagixMessage::origin.name, message.origin)
                setProperty(MagixMessage::format.name, message.format)

                setBlobString(MagixMessage::payload.name, MagixEndpoint.magixJson.encodeToString(message.payload))

                message.target?.let {
                    setProperty(MagixMessage::target.name, it)
                }
                message.id?.let {
                    setProperty(MagixMessage::id.name, it)
                }
                message.parentId?.let {
                    setProperty(MagixMessage::parentId.name, it)
                }
                message.user?.let {
                    setBlobString(MagixMessage::user.name, MagixEndpoint.magixJson.encodeToString(it))
                }
            }
        }
    }.launchIn(scope)

    override fun close() {
        subscriptionJob.cancel()
    }

    public companion object {
        public const val MAGIC_MESSAGE_ENTITY_TYPE: String = "magix.message"
    }
}