package space.kscience.controls.constructor

import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.DeviceTreeFactory
import space.kscience.controls.api.resolveDeviceOrNull
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
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
     * Provide an existing device property state from [DeviceManager] or late-bind state and actualize it when the device is connected.
     *
     * Throw an exception if the device is connected but does not have corresponding property
     *
     */
    public fun provideDevicePropertyState(deviceName: Name, propertyName: String): ValueState<Meta>{
        val existingDevice = deviceManager.resolveDeviceOrNull(deviceName)

        if( existingDevice != null){
            return existingDevice.propertyAsState(propertyName, MetaConverter.meta, Meta.EMPTY)
        } else {
            TODO("Late-binding state for device $deviceName is not implemented")
        }
    }

    /**
     * Create a Device (or device hub) from a serializable scheme using given value state factories
     */
    public fun construct(
        deviceConfiguration: ConstructorDeviceConfiguration,
    ): DeviceConstructor = DeviceConstructor(context, deviceConfiguration.parameters).apply {
        deviceConfiguration.devices.forEach { (name, scheme) ->
            install(name, construct(scheme))
        }

        deviceConfiguration.templates.forEach { (name, template) ->
            val factory = deviceManager.resolveDeviceFactory(template.type)
                ?: error("Device template type ${template.type} is not registered")
            installTree(name, factory, template.parameters)
        }

        deviceConfiguration.properties.forEach { (name, propertyConfiguration) ->
            registerProperty(
                name = name,
                converter = MetaConverter.meta,
                state = valueStateFactories[propertyConfiguration.type]?.build(
                    context,
                    propertyConfiguration.parameters
                )
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
    deviceConfiguration: ConstructorDeviceConfiguration
): DeviceConstructor = install(name, request(ConstructorPlugin).construct(deviceConfiguration))

public fun DeviceManager.install(
    name: String,
    deviceConfiguration: ConstructorDeviceConfiguration
): DeviceConstructor = context.install(name, deviceConfiguration)

public fun DeviceConstructor.install(
    name: String,
    deviceConfiguration: ConstructorDeviceConfiguration
): DeviceConstructor = install(name, context.request(ConstructorPlugin).construct(deviceConfiguration))

/**
 * A [DeviceTreeFactory] implemetation for a constructor device that uses [ConstructorDeviceConfiguration]
 */
public object ConstructorDeviceFactory : DeviceTreeFactory {

    override fun build(
        context: Context,
        meta: Meta
    ): DeviceTree {
        val deviceConfiguration = MetaConverter.serializable<ConstructorDeviceConfiguration>().read(meta)
        return context.request(ConstructorPlugin).construct(deviceConfiguration)
    }

    //TODO add descriptor for this
    override val descriptor: MetaDescriptor? = null

}