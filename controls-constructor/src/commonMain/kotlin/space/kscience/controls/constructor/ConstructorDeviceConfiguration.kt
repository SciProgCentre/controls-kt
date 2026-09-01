package space.kscience.controls.constructor

import kotlinx.serialization.Serializable
import space.kscience.controls.constructor.BoundStateHolder.Companion.DEFAULT_INPUT_NAME
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name

/**
 * Serializable scheme for Device ValueState construction
 */
@Serializable
public data class PropertyConfiguration(
    public val type: String,
    public val parameters: Meta,
    public val metadata: Meta = Meta.EMPTY
)

@Serializable
public data class TemplateDeviceConfiguration(
    public val type: String,
    public val parameters: Meta,
    public val metadata: Meta = Meta.EMPTY
)

@Serializable
public data class ConstructorBinding(
    val sourceDevice: Name,
    val sourceProperty: String,
    val targetDevice: Name,
    val targetInput: String = DEFAULT_INPUT_NAME,
    val defaultValue: Meta = Meta.EMPTY,
    public val metadata: Meta = Meta.EMPTY
    //TODO add transformations
)

/**
 * Serializable scheme for Device construction
 */
@Serializable
public class ConstructorDeviceConfiguration(
    public val properties: Map<String, PropertyConfiguration>,
    public val devices: Map<String, ConstructorDeviceConfiguration> = emptyMap(),
    public val components: Map<String, TemplateDeviceConfiguration> = emptyMap(),
    public val bindings: Set<ConstructorBinding> = emptySet(),
    public val parameters: Meta = Meta.EMPTY,
    public val metadata: Meta = Meta.EMPTY
)
//TODO add actions and setup/shutdown hooks