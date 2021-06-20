package ru.mipt.npm.magix.server

import io.ktor.application.ApplicationCall
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.response.cacheControl
import io.ktor.response.respondBytesWriter
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * The data class representing a SSE Event that will be sent to the client.
 */
public data class SseEvent(val data: String, val event: String? = "message", val id: String? = null)

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

@OptIn(KtorExperimentalAPI::class)
public suspend fun ApplicationCall.respondSse(events: Flow<SseEvent>) {
    response.cacheControl(CacheControl.NoCache(null))
    respondBytesWriter(contentType = ContentType.Text.EventStream) {
        writeSseFlow(events)
    }
}