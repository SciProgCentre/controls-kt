package space.kscience.controls.constructor

import kotlinx.serialization.Serializable
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter

/**
 * Serializable scheme for Device ValueState construction
 */
@Serializable
public class ConstructorStateScheme(
    public val name: String,
    public val type: String,
    public val parameters: Meta
)

/**
 * Serializable scheme for Device construction
 */
@Serializable
public class ConstructorDeviceScheme(
    public val devices: Map<String, ConstructorDeviceScheme>,
    public val properties: Map<String, ConstructorStateScheme>,
    public val parameters: Meta
)
//TODO add actions and setup/shutdown hooks

//TODO add models

public fun interface ValueStateProvider {
    public fun buildValueState(context: Context, parameters: Meta): ValueState<Meta>
}


/**
 * Create a Device (or device hub) from a serializable scheme using given value state factories
 */
public fun Context.buildDeviceGroup(
    scheme: ConstructorDeviceScheme,
    stateFactories: Map<String, ValueStateProvider> = ValueState.defaultValueStateFactories
): DeviceGroup = DeviceGroup(this, scheme.parameters).apply {
    scheme.devices.forEach { (name, scheme) -> install(name, buildDeviceGroup(scheme, stateFactories)) }
    scheme.properties.forEach { (name, stateScheme) ->
        registerAsProperty(
            name = name,
            converter = MetaConverter.meta,
            state = stateFactories[stateScheme.type]?.buildValueState(context, stateScheme.parameters)
                ?: error("No state factory for ${stateScheme.type}"),
        )
    }
}

/**
 * Install a Device (or device hub) from a serializable scheme using given value state factories
 */
public fun Context.install(
    name: String,
    scheme: ConstructorDeviceScheme,
    stateFactories: Map<String, ValueStateProvider>
): DeviceGroup = install(name, buildDeviceGroup(scheme, stateFactories))

