package space.kscience.magix.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter

class InMemoryMagixHistory() : WriteableMagixHistory {

    private val cache = mutableListOf<MagixMessage>()
    private val mutex = Mutex()

    override suspend fun send(message: MagixMessage) {
        mutex.withLock {
            cache.add(message)
        }
    }

    override suspend fun useMessages(
        magixFilter: MagixMessageFilter?,
        payloadFilter: MagixPayloadFilter?,
        userFilter: MagixUsernameFilter?,
        callback: (Sequence<MagixMessage>) -> Unit,
    ) = mutex.withLock {
        val sequence = cache.asSequence().filter { message ->
            (magixFilter?.accepts(message) ?: true) &&
                    (userFilter?.userName?.equals(message.user) ?: true) &&
                    payloadFilter?.test(message.payload) ?: true
        }
        callback(sequence)
    }
}