package space.kscience.controls.spec.utils

import space.kscience.controls.spec.config.DeviceHubConfig
import space.kscience.controls.spec.runtime.DeviceHubManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Plugin
import space.kscience.dataforge.context.PluginTag

/**
 * Extension to get [DeviceHubManager] from context.
 * @throws IllegalStateException if [DeviceHubManager] plugin is not registered.
 */
public val Context.deviceHubManager: DeviceHubManager
    get() = plugins[DeviceHubManager.tag] as? DeviceHubManager
        ?: throw IllegalStateException("DeviceHubManager plugin not found. Ensure registered: context.plugin(DeviceHubManager).")

/**
 * Extension to get [DeviceHubManager] from context, returning null if not found.
 */
public val Context.deviceHubManagerOrNull: DeviceHubManager?
    get() = plugins[DeviceHubManager.tag] as? DeviceHubManager

/**
 * Extension to get [DeviceHubConfig] from context.
 * @throws IllegalStateException if [DeviceHubConfig] plugin is not registered.
 */
public val Context.deviceManagerConfig: DeviceHubConfig
    get() = plugins[DeviceHubConfig.tag] as? DeviceHubConfig
        ?: throw IllegalStateException("DeviceHubConfig plugin not found. Ensure registered: context.plugin(DeviceHubConfig).")


/**
 * Requests a plugin of a specified type [T] from the context using its [pluginTag].
 * @throws IllegalStateException if the required plugin is not found or type mismatch.
 */
public inline fun <reified T : Plugin> Context.requirePlugin(pluginTag: PluginTag): T =
    plugins[pluginTag] as? T
        ?: throw IllegalStateException("Required plugin '${pluginTag.name}' (type ${T::class.simpleName}) not found or type mismatch in context $this.")