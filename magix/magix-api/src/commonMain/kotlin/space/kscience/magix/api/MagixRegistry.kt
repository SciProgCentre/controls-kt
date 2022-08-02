package space.kscience.magix.api

import kotlinx.serialization.json.JsonElement

/**
 * An interface to access distributed Magix property registry
 */
public interface MagixRegistry {
    /**
     * Request a property with name [propertyName] and user authentication data [user].
     *
     * Return a property value in its generic form or null if it is not present.
     *
     * Throw an exception if property is present but access is denied.
     */
    public suspend fun request(propertyName: String, user: JsonElement? = null): JsonElement?
}
