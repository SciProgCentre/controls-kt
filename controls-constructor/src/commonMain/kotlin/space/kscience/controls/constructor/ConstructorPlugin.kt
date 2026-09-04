package space.kscience.controls.constructor

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import space.kscience.controls.api.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.manager.messageFlow
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
     * Create a Device (or device hub) from a serializable scheme using given value state factories
     */
    public fun construct(
        deviceConfiguration: ConstructorDeviceConfiguration,
    ): DeviceConstructor = DeviceConstructor(context, deviceConfiguration.parameters).apply {
        deviceConfiguration.devices.forEach { (name, scheme) ->
            install(name, construct(scheme))
        }

        deviceConfiguration.components.forEach { (name, template) ->
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

        //apply bindings after all devices are created
        deviceConfiguration.bindings.forEach { binding ->
            val sourceDevice = resolveDevice(binding.sourceDevice)
            val sourceProperty = sourceDevice.propertyAsState(
                propertyName = binding.sourceProperty,
                metaConverter = MetaConverter.meta,
                initialValue = binding.defaultValue
            )
            val targetDevice = resolveDevice(binding.targetDevice) as? BoundStateHolder
                ?: error("Target device ${binding.targetDevice} is not a BoundStateHolder")
            targetDevice.bind(sourceProperty, binding.targetInput)
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

/**
 * Provide an existing device property state from [DeviceTree] or late-bind state and actualize it when the device is connected.
 *
 * Throw an exception if the device is connected but does not have a corresponding property
 *
 */
public fun DeviceTree.resolvePropertyState(
    context: Context,
    deviceName: Name,
    propertyName: String
): ValueState<Meta> {
    val existingDevice = resolveDeviceOrNull(deviceName)

    if (existingDevice != null) {
        return existingDevice.propertyAsState(propertyName, MetaConverter.meta, Meta.EMPTY)
    } else {
        context.logger.warn { "Requested property $propertyName of device $deviceName is not found. Using late-binding state instead." }
        val lateBindValueState = LateBindValueState(Meta.EMPTY)
        context.launch {
            messageFlow().filterIsInstance<DeviceLifeCycleMessage>().first {
                it.sourceDevice == deviceName && it.state == LifecycleState.STARTED
            }
            val device = resolveDeviceOrNull(deviceName)
            if (device != null) {
                lateBindValueState.bind(device.propertyAsState(propertyName, MetaConverter.meta, Meta.EMPTY))
            } else {
                context.logger.error { "Device $deviceName is not found after its start signal" }
            }
        }
        return lateBindValueState
    }
}

public fun DeviceConstructor.resolvePropertyState(deviceName: Name, propertyName: String): ValueState<Meta> =
    resolvePropertyState(context, deviceName, propertyName)

public fun Context.resolvePropertyState(deviceName: Name, propertyName: String): ValueState<Meta> =
    request(DeviceManager).resolvePropertyState(this, deviceName, propertyName)
