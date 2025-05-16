package space.kscience.controls.spec.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import space.kscience.controls.api.TransactionMessage.*
import space.kscience.controls.spec.infra.MessagingSystem
import space.kscience.controls.spec.utils.TimeSource
import space.kscience.dataforge.context.Logger
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.info
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Interface for a reversible action that can be undone during transaction rollback.
 */
public interface ReversibleAction {
    /** Unique action identifier. */
    public val id: String
    /** Reverses the action. Should be idempotent if possible. */
    public suspend fun reverse()
}

/**
 * Context for a single transaction, tracking its ID, start time, and recorded actions.
 */
public class TransactionContext internal constructor(
    public val id: String,
    private val timeSource: TimeSource,
    private val actions: MutableList<ReversibleAction> = mutableListOf()
) {
    public val startTime: Instant = timeSource.now()
    private val mutex = Mutex()
    private val savepoints = mutableMapOf<String, Int>()

    public suspend fun recordAction(action: ReversibleAction) {
        mutex.withLock { actions.add(action) }
    }

    public suspend fun createSavepoint(name: String): String {
        mutex.withLock {
            if (savepoints.containsKey(name)) {
                throw IllegalArgumentException("Savepoint '$name' already exists in TX '$id'.")
            }
            savepoints[name] = actions.size
        }
        return name
    }

    public suspend fun rollbackToSavepoint(name: String, logger: Logger? = null) {
        val actionsToRollback: List<ReversibleAction> = mutex.withLock {
            val index = savepoints[name] ?: throw IllegalArgumentException("Savepoint '$name' not found in TX '$id'.")
            val rollbackList = if (index < actions.size) actions.subList(index, actions.size).toList().asReversed() else emptyList()
            if (index < actions.size) actions.subList(index, actions.size).clear()
            savepoints.entries.removeAll { it.value > index }
            rollbackList
        }
        logger?.info { "Rolling back TX '$id' to savepoint '$name'. Reversing ${actionsToRollback.size} actions." }
        for (action in actionsToRollback) {
            try { action.reverse() }
            catch (e: Exception) { logger?.error(e) { "Error reversing action '${action.id}' during rollback to savepoint '$name' in TX '$id'." } }
        }
    }

    public suspend fun getActions(): List<ReversibleAction> = mutex.withLock { actions.toList() }
}

/**
 * CoroutineContext element holding the current [TransactionContext].
 */
private class TransactionContextElement(val context: TransactionContext) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<TransactionContextElement>
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * Manages transactional operations.
 */
public interface TransactionManager {
    public suspend fun <T> withTransaction(block: suspend (TransactionContext) -> T): T
    public suspend fun recordAction(action: ReversibleAction)
    public suspend fun isInTransaction(): Boolean
    public suspend fun createSavepoint(name: String): String
    public suspend fun rollbackToSavepoint(name: String)
}

/**
 * Default implementation of [TransactionManager].
 */
public class TransactionManagerImpl(
    private val messagingSystem: MessagingSystem,
    private val logger: Logger,
    private val timeSource: TimeSource
) : TransactionManager {
    private val GidManagerLock = Mutex()
    private val activeTransactions = mutableMapOf<String, TransactionContext>()

    override suspend fun <T> withTransaction(block: suspend (TransactionContext) -> T): T {
        val currentCoroutineCtx = coroutineContext
        val existingTxElement = currentCoroutineCtx[TransactionContextElement.Key]

        if (existingTxElement != null) {
            logger.info { "Joining existing transaction '${existingTxElement.context.id}'." }
            return block(existingTxElement.context)
        }

        val txId = "tx_${timeSource.now().toEpochMilliseconds()}_${(0..Int.MAX_VALUE).random().toString(16)}"
        val txContext = TransactionContext(txId, timeSource)

        try {
            GidManagerLock.withLock { activeTransactions[txId] = txContext }
            messagingSystem.publish(TransactionStartedMessage(txId, time = timeSource.now()))
            logger.info { "Transaction '$txId' started." }

            val result = withContext(currentCoroutineCtx + TransactionContextElement(txContext)) {
                block(txContext)
            }

            messagingSystem.publish(TransactionCommittedMessage(txId, time = timeSource.now()))
            logger.info { "Transaction '$txId' committed." }
            return result
        } catch (ex: Exception) {
            if (ex is CancellationException) {
                logger.info { "Transaction '$txId' cancelled." }
                messagingSystem.publish(TransactionRolledBackMessage(txId, "Transaction cancelled.", ex::class.simpleName, time = timeSource.now()))
                throw ex
            }
            logger.error(ex) { "Transaction '$txId' failed. Initiating rollback." }
            var rollbackError: Exception? = null
            try {
                val actionsToRollback = txContext.getActions().reversed()
                for (action in actionsToRollback) {
                    try { action.reverse() }
                    catch (undoEx: Exception) {
                        val msg = "Failed to reverse action '${action.id}' during rollback of TX '$txId'."
                        logger.error(undoEx) { msg }
                        val wrappedError = RuntimeException(msg, undoEx)
                        if (rollbackError == null) rollbackError = wrappedError else rollbackError.addSuppressed(wrappedError)
                    }
                }
            } catch (getActionEx: Exception) {
                logger.error(getActionEx) { "Critical error getting actions for rollback in TX '$txId'." }
                val criticalError = RuntimeException("Critical error during rollback prep for TX '$txId'", getActionEx)
                if (rollbackError == null) rollbackError = criticalError else rollbackError.addSuppressed(criticalError)
            }
            messagingSystem.publish(TransactionRolledBackMessage(txId, ex.message, ex::class.simpleName, time = timeSource.now()))
            rollbackError?.let { ex.addSuppressed(it) }
            throw ex
        } finally {
            GidManagerLock.withLock { activeTransactions.remove(txId) }
        }
    }

    override suspend fun recordAction(action: ReversibleAction) {
        val txCtx = coroutineContext[TransactionContextElement.Key]?.context
            ?: throw IllegalStateException("Cannot record action: Not in an active transaction.")
        txCtx.recordAction(action)
    }

    override suspend fun isInTransaction(): Boolean = coroutineContext[TransactionContextElement.Key] != null

    override suspend fun createSavepoint(name: String): String {
        val txCtx = coroutineContext[TransactionContextElement.Key]?.context
            ?: throw IllegalStateException("Cannot create savepoint: Not in an active transaction.")
        val savepointName = txCtx.createSavepoint(name)
        messagingSystem.publish(TransactionSavepointMessage(txCtx.id, savepointName, time = timeSource.now()))
        logger.info { "Savepoint '$savepointName' created in TX '${txCtx.id}'."}
        return savepointName
    }

    override suspend fun rollbackToSavepoint(name: String) {
        val txCtx = coroutineContext[TransactionContextElement.Key]?.context
            ?: throw IllegalStateException("Cannot rollback: Not in an active transaction.")
        txCtx.rollbackToSavepoint(name, logger)
        logger.info { "Rolled back to savepoint '$name' in TX '${txCtx.id}'."}
        // TODO publishing a "TransactionRolledBackToSavepointMessage" for observability.
    }
}