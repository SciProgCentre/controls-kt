package space.kscience.controls.constructor

import kotlinx.serialization.Serializable
import space.kscience.controls.constructor.BoundStateHolder.Companion.DEFAULT_INPUT_NAME
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name

/**
 * Serializable scheme for Device ValueState construction
 */
@Serializable
public data class ValueStateConfiguration(
    public val type: String,
    public val parameters: Meta,
    public val metadata: Meta = Meta.EMPTY
){
    public companion object{

        /**
         * Create a ValueStateConfiguration for a device property
         */
        public fun deviceProperty(
            deviceName: String,
            propertyName: String,
        ): ValueStateConfiguration = ValueStateConfiguration(
            type = DeviceValueStateFactory.TYPE,
            parameters = Meta {
                set(DeviceValueStateFactory.deviceName, deviceName)
                set(DeviceValueStateFactory.propertyName, propertyName)
            }
        )
    }
}


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
    public val properties: Map<String, ValueStateConfiguration>,
    public val devices: Map<String, ConstructorDeviceConfiguration> = emptyMap(),
    public val components: Map<String, TemplateDeviceConfiguration> = emptyMap(),
    public val bindings: Set<ConstructorBinding> = emptySet(),
    public val parameters: Meta = Meta.EMPTY,
    public val metadata: Meta = Meta.EMPTY
)
//TODO add actions and setup/shutdown hooks