package space.kscience.controls.ports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

/**
 * Transform byte fragments into complete phrases using given delimiter. Not thread safe.
 *
 * TODO add type wrapper for phrases
 */
public fun Flow<ByteArray>.withDelimiter(delimiter: ByteArray): Flow<ByteArray> {
    require(delimiter.isNotEmpty()) { "Delimiter must not be empty" }

    val output = Buffer()
    var matcherPosition = 0

    onCompletion {
        output.close()
    }

    return transform { chunk ->
        chunk.forEach { byte ->
            output.writeByte(byte)
            //matching current symbol in delimiter
            if (byte == delimiter[matcherPosition]) {
                matcherPosition++
                if (matcherPosition == delimiter.size) {
                    //full match achieved, sending result
                    emit(output.readByteArray())
                    output.clear()
                    matcherPosition = 0
                }
            } else if (matcherPosition > 0) {
                //Reset matcher since full match not achieved
                matcherPosition = 0
            }
        }
    }
}

private fun Flow<ByteArray>.withFixedMessageSize(messageSize: Int): Flow<ByteArray> {
    require(messageSize > 0) { "Message size should be positive" }

    val output = Buffer()

    onCompletion {
        output.close()
    }

    return transform { chunk ->
        val remaining: Int = (messageSize - output.size).toInt()
        if (chunk.size >= remaining) {
            output.write(chunk, endIndex = remaining)
            emit(output.readByteArray())
            output.clear()
            //write the remaining chunk fragment
            if(chunk.size> remaining) {
                output.write(chunk, startIndex = remaining)
            }
        } else {
            output.write(chunk)
        }
    }
}

/**
 * Transform byte fragments into utf-8 phrases using utf-8 delimiter
 */
public fun Flow<ByteArray>.withStringDelimiter(delimiter: String): Flow<String> {
    return withDelimiter(delimiter.encodeToByteArray()).map { it.decodeToString() }
}

/**
 * A flow of delimited phrases
 */
public fun AsynchronousPort.delimitedIncoming(delimiter: ByteArray): Flow<ByteArray> = subscribe().withDelimiter(delimiter)

/**
 * A flow of delimited phrases with string content
 */
public fun AsynchronousPort.stringsDelimitedIncoming(delimiter: String): Flow<String> = subscribe().withStringDelimiter(delimiter)
