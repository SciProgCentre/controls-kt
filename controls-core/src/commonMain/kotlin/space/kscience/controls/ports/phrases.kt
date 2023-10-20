package space.kscience.controls.ports

import io.ktor.utils.io.core.BytePacketBuilder
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.reset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform

/**
 * Transform byte fragments into complete phrases using given delimiter. Not thread safe.
 */
public fun Flow<ByteArray>.withDelimiter(delimiter: ByteArray): Flow<ByteArray> {
    require(delimiter.isNotEmpty()) { "Delimiter must not be empty" }

    val output = BytePacketBuilder()
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
                    val bytes = output.build()
                    emit(bytes.readBytes())
                    output.reset()
                    matcherPosition = 0
                }
            } else if (matcherPosition > 0) {
                //Reset matcher since full match not achieved
                matcherPosition = 0
            }
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
public fun Port.delimitedIncoming(delimiter: ByteArray): Flow<ByteArray> = receiving().withDelimiter(delimiter)

/**
 * A flow of delimited phrases with string content
 */
public fun Port.stringsDelimitedIncoming(delimiter: String): Flow<String> = receiving().withStringDelimiter(delimiter)
