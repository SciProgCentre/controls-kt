package space.kscience.magix.storage.xodus

import jetbrains.exodus.entitystore.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixEndpoint.Companion.magixJson
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import space.kscience.magix.storage.MagixHistory
import space.kscience.magix.storage.MagixPayloadFilter
import space.kscience.magix.storage.MagixUsernameFilter
import java.nio.file.Path
import kotlin.sequences.Sequence

/**
 * Attach a Xodus storage process to the given endpoint.
 */
public class XodusMagixStorage(
    scope: CoroutineScope,
    private val store: PersistentEntityStore,
    endpoint: MagixEndpoint,
    filter: MagixMessageFilter = MagixMessageFilter.ALL,
) : MagixHistory, AutoCloseable {

    //TODO consider message buffering
    internal val subscriptionJob = endpoint.subscribe(filter).onEach { message ->
        store.executeInTransaction { transaction ->
            transaction.newEntity(MAGIC_MESSAGE_ENTITY_TYPE).apply {
                setProperty(MagixMessage::sourceEndpoint.name, message.sourceEndpoint)
                setProperty(MagixMessage::format.name, message.format)

                setBlobString(MagixMessage::payload.name, MagixEndpoint.magixJson.encodeToString(message.payload))

                message.targetEndpoint?.let {
                    setProperty(MagixMessage::targetEndpoint.name, it)
                }
                message.id?.let {
                    setProperty(MagixMessage::id.name, it)
                }
                message.parentId?.let {
                    setProperty(MagixMessage::parentId.name, it)
                }
                message.user?.let {
                    setProperty(
                        MagixMessage::user.name,
                        when (it) {
                            is JsonObject -> it["name"]?.jsonPrimitive?.content ?: "@error"
                            is JsonPrimitive -> it.content
                            else -> "@error"
                        }
                    )
                }
            }
        }
    }.launchIn(scope)

    private fun Entity.parseMagixMessage(): MagixMessage = MagixMessage(
        format = getProperty(MagixMessage::format.name).toString(),
        payload = getBlobString(MagixMessage::payload.name)?.let {
            magixJson.parseToJsonElement(it)
        } ?: JsonObject(emptyMap()),
        sourceEndpoint = getProperty(MagixMessage::sourceEndpoint.name).toString(),
        targetEndpoint = getProperty(MagixMessage::targetEndpoint.name)?.toString(),
        id = getProperty(MagixMessage::id.name)?.toString(),
        parentId = getProperty(MagixMessage::parentId.name)?.toString(),
        user = getBlobString(MagixMessage::user.name)?.let {
            magixJson.parseToJsonElement(it)
        },
    )


    /**
     * Access all messages in a given format
     */
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

    /**
     * Access all messages as
     */
    public fun readAll(
        block: (Sequence<MagixMessage>) -> Unit,
    ): Unit = store.executeInReadonlyTransaction { transaction ->
        val sequence = transaction.getAll(MAGIC_MESSAGE_ENTITY_TYPE).asSequence().map { entity ->
            entity.parseMagixMessage()
        }
        block(sequence)
    }

    override suspend fun findMessages(
        magixFilter: MagixMessageFilter?,
        payloadFilters: List<MagixPayloadFilter>,
        userFilter: MagixUsernameFilter?,
        callback: (Sequence<MagixMessage>) -> Unit,
    ): Unit = store.executeInReadonlyTransaction { transaction ->
        val all = transaction.getAll(MAGIC_MESSAGE_ENTITY_TYPE)

        fun StoreTransaction.findAllIn(
            entityType: String,
            field: String,
            values: Collection<String>?,
        ): EntityIterable? {
            var union: EntityIterable? = null
            values?.forEach {
                val filter = transaction.find(entityType, field, it)
                union = union?.union(filter) ?: filter
            }
            return union
        }

        // filter by magix filter
        val filteredByMagix: EntityIterable = magixFilter?.let { mf ->
            var res = all
            transaction.findAllIn(MAGIC_MESSAGE_ENTITY_TYPE, MagixMessage::format.name, mf.format)?.let {
                res = res.intersect(it)
            }
            transaction.findAllIn(MAGIC_MESSAGE_ENTITY_TYPE, MagixMessage::sourceEndpoint.name, mf.origin)?.let {
                res = res.intersect(it)
            }
            transaction.findAllIn(MAGIC_MESSAGE_ENTITY_TYPE, MagixMessage::targetEndpoint.name, mf.target)?.let {
                res = res.intersect(it)
            }

            res
        } ?: all

        val filteredByUser: EntityIterable = userFilter?.let { userFilter->
            filteredByMagix.intersect(
                transaction.find(MAGIC_MESSAGE_ENTITY_TYPE, MagixMessage::user.name, userFilter.userName)
            )
        } ?: filteredByMagix


        filteredByUser.se


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