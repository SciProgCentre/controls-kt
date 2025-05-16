package space.kscience.controls.spec.api

import space.kscience.controls.spec.ConfigurableCompositeControlComponent
import space.kscience.controls.api.DeviceConfigurationException
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.names.Name

/**
 * Interface for a registry that stores and provides access to [CompositeControlComponentSpec]s.
 * This allows for dynamic lookup and instantiation of composite device specifications.
 */
public interface ComponentRegistry : ContextAware {
    /**
     * Retrieves a [CompositeControlComponentSpec] by its unique [name].
     *
     * @param D The expected type of the [ConfigurableCompositeControlComponent] the spec is for.
     * @param name The [Name] used to register the specification.
     * @return The [CompositeControlComponentSpec] if found and type matches, otherwise null.
     */
    public fun <D : ConfigurableCompositeControlComponent<D>> getSpec(name: Name): CompositeControlComponentSpec<D>?

    /**
     * Registers a [CompositeControlComponentSpec] with a given [name].
     *
     * @param D The type of the [ConfigurableCompositeControlComponent] the spec is for.
     * @param name The [Name] under which to register the specification.
     * @param spec The [CompositeControlComponentSpec] instance to register.
     * @throws DeviceConfigurationException if a specification with the same name is already registered
     *                                      and overwriting is not permitted by the implementation.
     */
    public fun <D : ConfigurableCompositeControlComponent<D>> registerSpec(
        name: Name,
        spec: CompositeControlComponentSpec<D>
    )

    /**
     * Checks if a specification with the given [name] exists in the registry.
     *
     * @param name The [Name] to check.
     * @return True if a specification with this name is registered, false otherwise.
     */
    public fun hasSpec(name: Name): Boolean

    /**
     * Lists the [Name]s of all specifications currently registered.
     *
     * @return A [Set] of [Name]s of all registered specifications.
     */
    public fun listSpecs(): Set<Name>
}

/**
 * Default in-memory implementation of [ComponentRegistry].
 * Stores specifications in a mutable map.
 *
 * @param context The parent [Context].
 */
public class DefaultComponentRegistry(
    override val context: Context
) : ComponentRegistry {
    private val registry = mutableMapOf<Name, CompositeControlComponentSpec<*>>()

    override fun <D : ConfigurableCompositeControlComponent<D>> getSpec(name: Name): CompositeControlComponentSpec<D>? {
        @Suppress("UNCHECKED_CAST")
        return registry[name] as? CompositeControlComponentSpec<D>
    }

    override fun <D : ConfigurableCompositeControlComponent<D>> registerSpec(
        name: Name,
        spec: CompositeControlComponentSpec<D>
    ) {
        if (registry.containsKey(name)) {
            context.logger.warn { "Overwriting specification for name '$name' in ComponentRegistry." }
        }
        registry[name] = spec
        context.logger.debug { "Registered specification for name '$name'."}
    }

    override fun hasSpec(name: Name): Boolean = name in registry

    override fun listSpecs(): Set<Name> = registry.keys.toSet()
}