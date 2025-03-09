package space.kscience.magix.storage.xodus

import jetbrains.exodus.entitystore.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixEndpoint.Companion.magixJson
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import space.kscience.magix.api.userName
import space.kscience.magix.storage.*
import java.nio.file.Path
import kotlin.sequences.Sequence


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

public class XodusMagixHistory(private val store: PersistentEntityStore) : WriteableMagixHistory {

    public fun writeMessage(storeTransaction: StoreTransaction, message: MagixMessage) {
        storeTransaction.newEntity(XodusMagixStorage.MAGIC_MESSAGE_ENTITY_TYPE).apply {
            setProperty(MagixMessage::sourceEndpoint.name, message.sourceEndpoint)
            setProperty(MagixMessage::format.name, message.format)

            setBlobString(MagixMessage::payload.name, magixJson.encodeToString(message.payload))

            setProperty(MagixMessage::targetEndpoint.name, (message.targetEndpoint ?: ""))

            message.id?.let {
                setProperty(MagixMessage::id.name, it)
            }
            message.parentId?.let {
                setProperty(MagixMessage::parentId.name, it)
            }
            message.userName?.let {
                setProperty(MagixMessage::user.name, it)
            }
        }
    }

    override suspend fun send(message: MagixMessage) {
        store.executeInTransaction { transaction ->
            writeMessage(transaction, message)
        }
    }

    override suspend fun useMessages(
        magixFilter: MagixMessageFilter?,
        payloadFilter: MagixPayloadFilter?,
        userFilter: MagixUsernameFilter?,
        callback: (Sequence<MagixMessage>) -> Unit,
    ): Unit = store.executeInReadonlyTransaction { transaction ->
        val all = transaction.getAll(XodusMagixStorage.MAGIC_MESSAGE_ENTITY_TYPE)

        fun findAllIn(
            entityType: String,
            field: String,
            values: Collection<String?>?,
        ): EntityIterable? {
            var union: EntityIterable? = null
            values?.forEach {
                val filter = transaction.find(entityType, field, it ?: "")
                union = union?.union(filter) ?: filter
            }
            return union
        }

        // filter by magix filter
        val filteredByMagix: EntityIterable = magixFilter?.let { mf ->
            var res = all
            findAllIn(
                XodusMagixStorage.MAGIC_MESSAGE_ENTITY_TYPE,
                MagixMessage::format.name,
                mf.format
            )?.let {
                res = res.intersect(it)
            }
            findAllIn(
                XodusMagixStorage.MAGIC_MESSAGE_ENTITY_TYPE,
                MagixMessage::sourceEndpoint.name,
                mf.source
            )?.let {
                res = res.intersect(it)
            }
            findAllIn(
                XodusMagixStorage.MAGIC_MESSAGE_ENTITY_TYPE,
                MagixMessage::targetEndpoint.name,
                mf.target?.filterNotNull()
            )?.let {
                res = res.intersect(it)
            }

            res
        } ?: all

        val filteredByUser: EntityIterable = userFilter?.let { userFilter ->
            filteredByMagix.intersect(
                transaction.find(
                    XodusMagixStorage.MAGIC_MESSAGE_ENTITY_TYPE,
                    MagixMessage::user.name,
                    userFilter.userName
                )
            )
        } ?: filteredByMagix


        val sequence = filteredByUser.asSequence().map { it.parseMagixMessage() }

        val filteredSequence = if (payloadFilter == null) {
            sequence
        } else {
            sequence.filter {
                payloadFilter.test(it.payload)
            }
        }

        callback(filteredSequence)
    }
}


/**
 * Attach a Xodus storage process to the given endpoint.
 */
public class XodusMagixStorage(
    scope: CoroutineScope,
    private val store: PersistentEntityStore,
    endpoint: MagixEndpoint,
    endpointName: String? = null,
    subscriptionFilter: MagixMessageFilter = MagixMessageFilter.ALL,
) : AutoCloseable {

    public val history: XodusMagixHistory = XodusMagixHistory(store)

    //TODO consider message buffering
    private val subscriptionJob = endpoint.subscribe(subscriptionFilter).onEach { message ->
        history.send(message)
    }.launchIn(scope)

    private val broadcastJob = endpoint.launchHistory(scope, history, endpointName = endpointName)


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
    endpointName: String? = null,
    filter: MagixMessageFilter = MagixMessageFilter(),
): XodusMagixStorage = XodusMagixStorage(scope, xodusStore, this, endpointName, filter)

public fun MagixEndpoint.storeInXodus(
    scope: CoroutineScope,
    path: Path,
    endpointName: String? = null,
    filter: MagixMessageFilter = MagixMessageFilter(),
): XodusMagixStorage {
    val store = PersistentEntityStores.newInstance(path.toFile())
    return XodusMagixStorage(scope, store, this, endpointName, filter)
}