package hep.dataforge.control.ports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.io.ByteArrayOutput

/**
 * Transform byte fragments into complete phrases using given delimiter
 */
public fun Flow<ByteArray>.withDelimiter(delimiter: ByteArray, expectedMessageSize: Int = 32): Flow<ByteArray> = flow {
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

/**
 * Transform byte fragments into utf-8 phrases using utf-8 delimiter
 */
public fun Flow<ByteArray>.withDelimiter(delimiter: String, expectedMessageSize: Int = 32): Flow<String> {
    return withDelimiter(delimiter.encodeToByteArray(),expectedMessageSize).map { it.decodeToString() }
}

/**
 * A flow of delimited phrases
 */
public suspend fun Port.delimitedIncoming(delimiter: ByteArray, expectedMessageSize: Int = 32): Flow<ByteArray> =
    receiving().withDelimiter(delimiter, expectedMessageSize)
