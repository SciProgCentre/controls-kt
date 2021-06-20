package ru.mipt.npm.magix.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


/*
 * {
 *  "format": "string[required]",
 *  "id":"string|number[optional, but desired]",
 *  "parentId": "string|number[optional]",
 *  "target":"string[optional]",
 *  "origin":"string[required]",
 *  "user":"string[optional]",
 *  "action":"string[optional, default='heartbeat']",
 *  "payload":"object[optional]"
 * }
 */

/**
 *
 * Magix message according to [magix specification](https://github.com/piazza-controls/rfc/tree/master/1)
 * with a [correction](https://github.com/piazza-controls/rfc/issues/12)
 *
 */
@Serializable
public data class MagixMessage<T>(
    val format: String,
    val origin: String,
    val payload: T,
    val target: String? = null,
    val id: String? = null,
    val parentId: String? = null,
    val user: JsonElement? = null,
)

/**
 * Create message with same field but replaced payload
 */
@Suppress("UNCHECKED_CAST")
public fun <T, R> MagixMessage<T>.replacePayload(payloadTransform: (T) -> R): MagixMessage<R> =
    MagixMessage(format, origin, payloadTransform(payload), target, id, parentId, user)