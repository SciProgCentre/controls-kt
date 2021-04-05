package space.kscience.dataforge.magix.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 *
 * Magix message according to [magix specification](https://github.com/piazza-controls/rfc/tree/master/1)
 * with a [correction](https://github.com/piazza-controls/rfc/issues/12)
 *
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
@Serializable
public data class MagixMessage<T>(
    val format: String,
    val origin: String,
    val payload: T,
    val target: String? = null,
    val id: String?  = null,
    val parentId: String? = null,
    val user: JsonElement? = null,
    val action: String? = null
)