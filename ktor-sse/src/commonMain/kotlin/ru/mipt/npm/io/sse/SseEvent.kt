package ru.mipt.npm.io.sse

import io.ktor.utils.io.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive


/**
 * The data class representing a SSE Event that will be sent to the client.
 */
public data class SseEvent(val data: String, val event: String? = null, val id: String? = null)

public suspend fun ByteWriteChannel.writeSseFlow(events: Flow<SseEvent>): Unit = events.collect { event ->
    if (event.id != null) {
        writeStringUtf8("id: ${event.id}\n")
    }
    if (event.event != null) {
        writeStringUtf8("event: ${event.event}\n")
    }
    for (dataLine in event.data.lines()) {
        writeStringUtf8("data: $dataLine\n")
    }
    writeStringUtf8("\n")
    flush()
}

@OptIn(ExperimentalCoroutinesApi::class)
public suspend fun ByteReadChannel.readSseFlow(): Flow<SseEvent> = channelFlow {
    while (isActive) {
        //val lines = ArrayList<String>()
        val builder = StringBuilder()
        var id: String? = null
        var event: String? = null
        //read lines until blank line or the end of stream

        do{
            val line = readUTF8Line()
            if (line != null && !line.isBlank()) {
                val key = line.substringBefore(":")
                val value = line.substringAfter(": ")
                when (key) {
                    "id" -> id = value
                    "event" -> event = value
                    "data" -> builder.append(value)
                    else -> error("Unrecognized event-stream key $key")
                }
            }
        } while (line?.isBlank() != true)
        if(builder.isNotBlank()) {
            send(SseEvent(builder.toString(), event, id))
        }
    }
    awaitClose {
        this@readSseFlow.cancel()
    }
}
