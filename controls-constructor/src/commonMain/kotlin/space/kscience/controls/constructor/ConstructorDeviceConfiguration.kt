package space.kscience.controls.constructor

import kotlinx.serialization.Serializable
import space.kscience.controls.constructor.BoundStateHolder.Companion.DEFAULT_INPUT_NAME
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName

/**
 * Serializable scheme for Device ValueState construction
 */
@Serializable
public data class ValueStateConfiguration(
    public val type: String,
    public val attributes: Meta,
) {
    public companion object {

        public val metaConverter: MetaConverter<ValueStateConfiguration> =
            MetaConverter.serializable<ValueStateConfiguration>()

        /**
         * Create a ValueStateConfiguration for a device property
         *
         * @param attributes custom attributes builder
         */
        public fun deviceProperty(
            deviceName: Name,
            propertyName: String,
            attributes: MutableMeta.() -> Unit = {}
        ): ValueStateConfiguration = ValueStateConfiguration(
            type = DeviceValueStateFactory.TYPE,
            attributes = Meta {
                attributes()
                set(DeviceValueStateFactory.deviceName, deviceName.toString())
                set(DeviceValueStateFactory.propertyName, propertyName)
            }
        )

        public fun deviceProperty(
            deviceName: String,
            propertyName: String,
            attributes: MutableMeta.() -> Unit = {}
        ): ValueStateConfiguration = deviceProperty(deviceName.parseAsName(), propertyName, attributes)
    }
}


@Serializable
public data class TemplateDeviceConfiguration(
    public val type: String,
    public val attributes: Meta,
)

@Serializable
public data class ConstructorBinding(
    val sourceDevice: Name,
    val sourceProperty: String,
    val targetDevice: Name,
    val targetInput: String = DEFAULT_INPUT_NAME,
    val defaultValue: Meta = Meta.EMPTY,
    //TODO add transformations
)

/**
 * Serializable scheme for Device construction
 */
@Serializable
public class ConstructorDeviceConfiguration(
    public val properties: Map<String, ValueStateConfiguration> = emptyMap(),
    public val devices: Map<String, ConstructorDeviceConfiguration> = emptyMap(),
    public val components: Map<String, TemplateDeviceConfiguration> = emptyMap(),
    public val bindings: Set<ConstructorBinding> = emptySet(),
    public val attributes: Meta = Meta.EMPTY,
)
//TODO add actions and setup/shutdown hooks