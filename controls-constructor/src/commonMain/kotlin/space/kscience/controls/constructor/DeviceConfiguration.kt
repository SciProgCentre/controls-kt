package space.kscience.controls.constructor

import kotlinx.serialization.Serializable
import space.kscience.dataforge.meta.Meta

/**
 * Serializable scheme for Device ValueState construction
 */
@Serializable
public class PropertyConfiguration(
    public val type: String,
    public val parameters: Meta
)

/**
 * Serializable scheme for Device construction
 */
@Serializable
public class DeviceConfiguration(
    public val properties: Map<String, PropertyConfiguration>,
    public val devices: Map<String, DeviceConfiguration> = emptyMap(),
    public val parameters: Meta = Meta.EMPTY,
)
//TODO add actions and setup/shutdown hooks

//TODO add models
