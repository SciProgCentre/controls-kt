package space.kscience.magix.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixFormat
import space.kscience.magix.api.send
import space.kscience.magix.api.subscribe

/**
 * An interface to access distributed Magix property registry
 */
public interface MagixRegistry {
    /**
     * Request a property with name [propertyName].
     *
     * Return a property value in its generic form or null if it is not present.
     *
     * Throw exception access is denied, or request failed.
     */
    public suspend fun get(propertyName: String): JsonElement?
}

public interface MutableMagixRegistry {
    public suspend fun set(propertyName: String, value: JsonElement?, user: JsonElement?)
}

@Serializable
public sealed class MagixRegistryMessage {
    public abstract val propertyName: String

    public companion object {
        public val format: MagixFormat<MagixRegistryMessage> = MagixFormat(serializer(), setOf("magix.registry"))
    }
}

@Serializable
@SerialName("registry.request")
public class MagixRegistryRequestMessage(
    override val propertyName: String,
) : MagixRegistryMessage()

@Serializable
@SerialName("registry.value")
public class MagixRegistryValueMessage(
    override val propertyName: String,
    public val value: JsonElement?,
) : MagixRegistryMessage()

@Serializable
@SerialName("registry.error")
public class MagixRegistryErrorMessage(
    override val propertyName: String,
    public val errorType: String?,
    public val errorMessage: String? = null,
) : MagixRegistryMessage()

@Serializable
@SerialName("registry.modify")
public class MagixRegistryModifyMessage(
    override val propertyName: String,
    public val value: JsonElement,
) : MagixRegistryMessage()

/**
 * Launch a magix registry loop service based on local registry
 */
public fun CoroutineScope.launchMagixRegistry(
    endpointName: String,
    endpoint: MagixEndpoint,
    registry: MagixRegistry,
    originFilter: Collection<String>? = null,
    targetFilter: Collection<String>? = null,
): Job = endpoint.subscribe(MagixRegistryMessage.format, originFilter, targetFilter)
    .onEach { (magixMessage, payload) ->
        try {
            when {
                payload is MagixRegistryRequestMessage -> {
                    endpoint.send(
                        MagixRegistryMessage.format,
                        MagixRegistryValueMessage(payload.propertyName, registry.get(payload.propertyName) ?: JsonNull),
                        source = endpointName,
                        target = magixMessage.sourceEndpoint,
                        parentId = magixMessage.id
                    )
                }

                payload is MagixRegistryModifyMessage && registry is MutableMagixRegistry -> {
                    registry.set(payload.propertyName, payload.value, magixMessage.user)
                    // Broadcast updates. Do not set target
                    endpoint.send(
                        MagixRegistryMessage.format,
                        MagixRegistryValueMessage(
                            payload.propertyName,
                            registry.get(payload.propertyName)
                        ),
                        source = endpointName,
                        parentId = magixMessage.id
                    )
                }
            }
        } catch (ex: Exception) {
            endpoint.send(
                MagixRegistryMessage.format,
                MagixRegistryErrorMessage(payload.propertyName, ex::class.simpleName, ex.message),
                source = endpointName,
                target = magixMessage.sourceEndpoint,
                parentId = magixMessage.id
            )
        }
    }.launchIn(this)

/**
 * Request a property with given name and return a [Flow] of pairs (sourceEndpoint, value).
 *
 * Flow is ordered by response receive time.
 * The subscriber can terminate the flow at any moment to stop subscription, or use it indefinitely to continue observing changes.
 * To request a single value, use [Flow.first] function.
 *
 * If [registryEndpoint] field is provided, send request only to given endpoint.
 *
 * @param sourceEndpoint the name of endpoint requesting a property
 */
public suspend fun MagixEndpoint.getProperty(
    propertyName: String,
    sourceEndpoint: String,
    user: JsonElement? = null,
    registryEndpoint: String? = null,
): Flow<Pair<String, JsonElement>> = subscribe(
    MagixRegistryMessage.format,
    originFilter = registryEndpoint?.let { setOf(it) }
).mapNotNull { (message, response) ->
    if (response is MagixRegistryValueMessage && response.propertyName == propertyName) {
        message.sourceEndpoint to (response.value ?: return@mapNotNull null)
    } else null
}.also {
    //send the initial request after subscription
    send(
        MagixRegistryMessage.format,
        MagixRegistryRequestMessage(propertyName),
        source = sourceEndpoint,
        target = registryEndpoint,
        user = user
    )
}