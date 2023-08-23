package space.kscience.magix.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A plugin that could be inserted into basic loop implementation.
 */
public fun interface MagixFlowPlugin {

    /**
     * Attach a [Job] to magix loop.
     * Receive messages from [receive].
     * Send messages via [sendMessage]
     */
    public fun start(
        scope: CoroutineScope,
        receive: Flow<MagixMessage>,
        sendMessage: suspend (MagixMessage) -> Unit,
    ): Job

    /**
     * Use the same [MutableSharedFlow] to send and receive messages. Could be a bottleneck in case of many plugins.
     */
    public fun start(scope: CoroutineScope, magixFlow: MutableSharedFlow<MagixMessage>): Job =
        start(scope, magixFlow) { magixFlow.emit(it) }
}