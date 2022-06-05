package ru.mipt.npm.magix.storage.xodus

import jetbrains.exodus.entitystore.Entity
import jetbrains.exodus.entitystore.PersistentEntityStore
import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixEndpoint.Companion.magixJson
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter
import java.nio.file.Path

public class XodusMagixStorage(
    scope: CoroutineScope,
    private val store: PersistentEntityStore,
    endpoint: MagixEndpoint,
    filter: MagixMessageFilter = MagixMessageFilter(),
) : AutoCloseable {

    //TODO consider message buffering
    internal val subscriptionJob = endpoint.subscribe(filter).onEach { message ->
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

    private fun Entity.parseMagixMessage(): MagixMessage =             MagixMessage(
        format = getProperty(MagixMessage::format.name).toString(),
        payload = getBlobString(MagixMessage::payload.name)?.let {
            magixJson.parseToJsonElement(it)
        } ?: JsonObject(emptyMap()),
        origin = getProperty(MagixMessage::origin.name).toString(),
        target = getProperty(MagixMessage::target.name)?.toString(),
        id = getProperty(MagixMessage::id.name)?.toString(),
        parentId = getProperty(MagixMessage::parentId.name)?.toString(),
        user = getBlobString(MagixMessage::user.name)?.let {
            magixJson.parseToJsonElement(it)
        },
    )

    public fun readByFormat(
        format: String,
        block: (Sequence<MagixMessage>) -> Unit,
    ): Unit = store.executeInReadonlyTransaction { transaction ->
        val sequence = transaction.find(
            MAGIC_MESSAGE_ENTITY_TYPE,
            MagixMessage::format.name,
            format
        ).asSequence().map { entity ->
            entity.parseMagixMessage()
        }
        block(sequence)
    }

    public fun readAll(
        block: (Sequence<MagixMessage>) -> Unit,
    ): Unit = store.executeInReadonlyTransaction { transaction ->
        val sequence = transaction.getAll(MAGIC_MESSAGE_ENTITY_TYPE).asSequence().map { entity ->
            entity.parseMagixMessage()
        }
        block(sequence)
    }

    override fun close() {
        subscriptionJob.cancel()
    }

    public companion object {
        public const val MAGIC_MESSAGE_ENTITY_TYPE: String = "magix.message"
    }
}

/**
 * Start writing all incoming messages with given [filter] to [xodusStore]
 */
public fun MagixEndpoint.storeInXodus(
    scope: CoroutineScope,
    xodusStore: PersistentEntityStore,
    filter: MagixMessageFilter = MagixMessageFilter(),
): XodusMagixStorage = XodusMagixStorage(scope, xodusStore, this, filter)

public fun MagixEndpoint.storeInXodus(
    scope: CoroutineScope,
    path: Path,
    filter: MagixMessageFilter = MagixMessageFilter(),
): XodusMagixStorage {
    val store = PersistentEntityStores.newInstance(path.toFile())
    return XodusMagixStorage(scope, store, this, filter)
}