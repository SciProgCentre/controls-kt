package space.kscience.magix.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessageFilter

/**
 * Create a gateway between two magix endpoints using filters for forward and backward message passing.
 * Portal is useful to create segmented magix loops:
 * * limit the load on given loop segment by filtering some messages;
 * * use different loop implementations.
 */
public fun CoroutineScope.launchMagixPortal(
    firstEndpoint: MagixEndpoint,
    secondEndpoint: MagixEndpoint,
    forwardFilter: MagixMessageFilter = MagixMessageFilter.ALL,
    backwardFilter: MagixMessageFilter = MagixMessageFilter.ALL,
): Job = launch {
    firstEndpoint.subscribe(forwardFilter).onEach {
        secondEndpoint.broadcast(it)
    }.launchIn(this)

    secondEndpoint.subscribe(backwardFilter).onEach {
        firstEndpoint.broadcast(it)
    }.launchIn(this)
}
