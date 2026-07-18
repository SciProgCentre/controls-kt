package space.kscience.controls.constructor

import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.DeviceTreeFactory
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.last

/**
 * A plugin that allows constructing value states from meta.
 */
public class ConstructorPlugin : AbstractPlugin() {

    public val deviceManager: DeviceManager by require(DeviceManager)

    public val valueStateFactories: Map<String, ValueStateFactory> by lazy {
        context.gather(ValueStateFactory.PROVIDER_TAGET, ValueStateFactory::class).mapKeys { it.key.last().toString() }
    }

    public fun buildValueState(parameters: Meta): ValueState<Meta> {
        val type = parameters["type"]?.string ?: error("Type not specified")
        return valueStateFactories[type]?.build(context, parameters) ?: error("No value state factory for type $type")
    }

    override fun content(target: String): Map<Name, Any> = when (target) {
        ValueStateFactory.PROVIDER_TAGET -> mapOf(
            Name.of("deviceProperty") to DeviceValueStateFactory,
            Name.of("expression") to ExpressionValueStateFactory
        )

        DeviceManager.DEVICE_FACTORY_TARGET -> mapOf(
            Name.of("constructor") to ConstructorDeviceFactory
        )

        else -> super.content(target)
    }

    /**
     * Create a Device (or device hub) from a serializable scheme using given value state factories
     */
    public fun construct(
        deviceConfiguration: DeviceConfiguration,
    ): DeviceConstructor = DeviceConstructor(context, deviceConfiguration.parameters).apply {
        deviceConfiguration.devices.forEach { (name, scheme) ->
            install(name, construct(scheme))
        }
        deviceConfiguration.properties.forEach { (name, propertyConfiguration) ->
            registerProperty(
                name = name,
                converter = MetaConverter.meta,
                state = valueStateFactories[propertyConfiguration.type]?.build(context, propertyConfiguration.parameters)
                    ?: error("No value state factory for ${propertyConfiguration.type}. Available factories: ${valueStateFactories.keys}"),
            )
        }
    }

    override val tag: PluginTag get() = Companion.tag

    public companion object : PluginFactory<ConstructorPlugin> {
        override val tag: PluginTag = PluginTag("controls.constructor")

        override fun build(
            context: Context,
            meta: Meta
        ): ConstructorPlugin = ConstructorPlugin()
    }
}

/**
 * Install a Device (or device hub) from a serializable scheme using given value state factories
 */
public fun Context.install(
    name: String,
    deviceConfiguration: DeviceConfiguration
): DeviceConstructor = install(name, request(ConstructorPlugin).construct(deviceConfiguration))

public fun DeviceManager.install(
    name: String,
    deviceConfiguration: DeviceConfiguration
): DeviceConstructor = context.install(name, deviceConfiguration)

/**
 * A [DeviceTreeFactory] implemetation for a constructor device that uses [DeviceConfiguration]
 */
public object ConstructorDeviceFactory : DeviceTreeFactory {

    override fun build(
        context: Context,
        meta: Meta
    ): DeviceTree {
        @OptIn(DFExperimental::class)
        val deviceConfiguration = MetaConverter.serializable<DeviceConfiguration>().read(meta)
        return context.request(ConstructorPlugin).construct(deviceConfiguration)
    }

    //TODO add descriptor for this
    override val descriptor: MetaDescriptor? = null

}