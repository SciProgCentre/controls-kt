package hep.dataforge.control.ports

import kotlinx.coroutines.CancellationException
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

abstract class Port(val scope: CoroutineScope) : Closeable {

    abstract val logger: KLogger

    private val outgoing = Channel<ByteArray>(100)
    private val incoming = Channel<ByteArray>(Channel.CONFLATED)
    val receiveChannel: ReceiveChannel<ByteArray> get() = incoming

    /**
     * Internal method to synchronously send data
     */
    protected abstract suspend fun write(data: ByteArray)

    /**
     * Internal method to receive data synchronously
     */
    protected fun receive(data: ByteArray) {
        scope.launch {
            logger.debug { "RECEIVED: ${data.decodeToString()}" }
            incoming.send(data)
        }
    }

    private val sendJob = scope.launch {
        for (data in outgoing) {
            try {
                write(data)
                logger.debug { "SENT: ${data.decodeToString()}" }
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                logger.error(ex) { "Error while writing data to the port" }
            }
        }
    }

    suspend fun send(data: ByteArray) {
        outgoing.send(data)
    }

    /**
     * Raw flow of incoming data chunks. The chunks are not guaranteed to be complete phrases.
     * In order to form phrases some condition should used on top of it.
     * For example [delimitedInput] generates phrases with fixed delimiter.
     */
    fun input(): Flow<ByteArray> {
        return incoming.receiveAsFlow()
    }

    override fun close() {
        scope.cancel("The port is closed")
        outgoing.close()
        incoming.close()
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

fun Port.delimitedInput(delimiter: ByteArray, expectedMessageSize: Int = 32) =
    input().withDelimiter(delimiter, expectedMessageSize)
