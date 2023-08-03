package space.kscience.magix.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*


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
    val format: String,
    val payload: JsonElement,
    val sourceEndpoint: String,
    val targetEndpoint: String? = null,
    val id: String? = null,
    val parentId: String? = null,
    val user: JsonElement? = null,
)

/**
 * The default accessor for username. If `user` is an object, take it's "name" field.
 * If it is primitive, take its content. Return "@error" if it is an array.
 */
public val MagixMessage.userName: String? get() = when(user){
    null, JsonNull -> null
    is JsonObject -> user.jsonObject["name"]?.jsonPrimitive?.content
    is JsonPrimitive -> user.content
    else -> "@error"
}