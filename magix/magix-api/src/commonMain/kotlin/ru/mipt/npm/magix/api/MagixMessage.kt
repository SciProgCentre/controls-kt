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
public data class MagixMessage(
    val origin: String,
    val payload: JsonElement,
    val format: String = origin,
    val target: String? = null,
    val id: String? = null,
    val parentId: String? = null,
    val user: JsonElement? = null,
)