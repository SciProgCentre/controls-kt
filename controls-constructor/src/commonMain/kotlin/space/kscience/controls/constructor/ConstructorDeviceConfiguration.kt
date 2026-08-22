package space.kscience.controls.constructor

import kotlinx.serialization.Serializable
import space.kscience.dataforge.meta.Meta

/**
 * Serializable scheme for Device ValueState construction
 */
@Serializable
public data class PropertyConfiguration(
    public val type: String,
    public val parameters: Meta
)

@Serializable
public data class TemplateDeviceConfiguration(
    public val type: String,
    public val parameters: Meta
)

/**
 * Serializable scheme for Device construction
 */
@Serializable
public class ConstructorDeviceConfiguration(
    public val properties: Map<String, PropertyConfiguration>,
    public val devices: Map<String, ConstructorDeviceConfiguration> = emptyMap(),
    public val templates: Map<String, TemplateDeviceConfiguration> = emptyMap(),
    public val parameters: Meta = Meta.EMPTY,
)
//TODO add actions and setup/shutdown hooks