package space.kscience.magix.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow

public fun interface MagixFlowPlugin {
    public fun start(scope: CoroutineScope, magixFlow: MutableSharedFlow<MagixMessage>): Job
}