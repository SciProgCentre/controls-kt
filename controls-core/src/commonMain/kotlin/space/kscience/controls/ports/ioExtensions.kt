package space.kscience.controls.ports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.io.Buffer
import kotlinx.io.Source
import space.kscience.dataforge.io.Binary

public fun Binary.readShort(position: Int): Short = read(position) { readShort() }

/**
 * Consume given flow of [ByteArray] as [Source]. The subscription is canceled when [scope] is closed.
 */
public fun Flow<ByteArray>.consumeAsSource(scope: CoroutineScope): Source {
    val buffer = Buffer()
    //subscription is canceled when the scope is canceled
    onEach {
        buffer.write(it)
    }.launchIn(scope)

    return buffer
}