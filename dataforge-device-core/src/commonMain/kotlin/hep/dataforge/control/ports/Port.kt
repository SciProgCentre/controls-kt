package hep.dataforge.control.ports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.io.ByteArrayOutput
import kotlinx.io.Closeable
import mu.KLogger

abstract class Port : Closeable, CoroutineScope {

    abstract val logger: KLogger

    private val outgoing = Channel<ByteArray>(100)
    private val incoming = Channel<ByteArray>(Channel.CONFLATED)
    val receiveChannel: ReceiveChannel<ByteArray> get() = incoming

    /**
     * Internal method to synchronously send data
     */
    protected abstract fun sendInternal(data: ByteArray)

    /**
     * Internal method to receive data synchronously
     */
    protected fun receive(data: ByteArray) {
        launch {
            incoming.send(data)
        }
    }

    private val sendJob = launch {
        //using special dispatcher to avoid threading problems
        for (data in outgoing) {
            try {
                sendInternal(data)
                logger.debug { "SEND: ${data.decodeToString()}" }
            } catch (ex: Exception) {
                logger.error(ex) { "Error while sending data" }
            }
        }
    }

    suspend fun send(data: ByteArray) {
        outgoing.send(data)
    }

    fun flow(): Flow<ByteArray> {
        return incoming.receiveAsFlow()
    }

    override fun close() {
        cancel("The port is closed")
    }
}

/**
 * Send UTF-8 encoded string
 */
suspend fun Port.send(string: String) = send(string.encodeToByteArray())

fun Flow<ByteArray>.withDelimiter(delimiter: ByteArray, expectedMessageSize: Int = 32): Flow<ByteArray> = flow {
    require(delimiter.isNotEmpty()) { "Delimiter must not be empty" }

    var output = ByteArrayOutput(expectedMessageSize)
    var matcherPosition = 0

    collect { chunk ->
        chunk.forEach { byte ->
            output.writeByte(byte)
            //matching current symbol in delimiter
            if (byte == delimiter[matcherPosition]) {
                matcherPosition++
                if (matcherPosition == delimiter.size) {
                    //full match achieved, sending result
                    emit(output.toByteArray())
                    output = ByteArrayOutput(expectedMessageSize)
                    matcherPosition = 0
                }
            } else if (matcherPosition > 0) {
                //Reset matcher since full match not achieved
                matcherPosition = 0
            }
        }
    }
}
