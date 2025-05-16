package space.kscience.controls.spec.api

import space.kscience.controls.spec.ConfigurableCompositeControlComponent
import space.kscience.controls.spec.config.DeviceLifecycleConfig
import space.kscience.controls.spec.config.DeviceLifecycleConfigBuilder
import space.kscience.controls.spec.model.ChildDeviceErrorHandler
import space.kscience.controls.spec.model.LifecycleMode
import space.kscience.controls.spec.utils.ParsingUtils // Assuming ParsingUtils is moved here
import space.kscience.dataforge.context.Logger
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName

/**
 * Interface representing the configuration for a child component within a composite device.
 * It links a child's [Name] to its [CompositeControlComponentSpec], [DeviceLifecycleConfig],
 * and optional [Meta].
 *
 * @param CD The type of the child's [ConfigurableCompositeControlComponent].
 */
public interface ChildComponentConfig<CD : ConfigurableCompositeControlComponent<CD>> {
    /** The [CompositeControlComponentSpec] that defines the child component. */
    public val spec: CompositeControlComponentSpec<CD>
    /** The [DeviceLifecycleConfig] for managing the child component's lifecycle. */
    public val config: DeviceLifecycleConfig
    /** Optional [Meta] providing additional configuration or metadata for the child component. */
    public val meta: Meta?
    /** The unique [Name] of the child component within its parent. */
    public val name: Name

    public companion object {
        /**
         * Creates a [ChildComponentConfig] instance from a [Meta] object.
         * This allows defining child component configurations declaratively.
         *
         * @param CD The type of the child's [ConfigurableCompositeControlComponent].
         * @param meta The [Meta] object containing the child's configuration.
         * @param registry The [ComponentRegistry] used to look up the child's [spec] by name.
         * @param name The intended [Name] for this child component.
         * @param logger Optional [Logger] for reporting issues during parsing.
         * @return A [ChildComponentConfig] instance if parsing is successful, null otherwise.
         */
        public fun <CD : ConfigurableCompositeControlComponent<CD>> fromMeta(
            meta: Meta,
            registry: ComponentRegistry,
            name: Name, // Name of the child instance
            logger: Logger? = null
        ): ChildComponentConfig<CD>? {
            val specNameString = meta["spec"].string ?: run {
                logger?.warn { "Child component '$name' in Meta is missing the required 'spec' field (string)." }
                return null
            }
            val specName = specNameString.asName()

            val spec: CompositeControlComponentSpec<CD> = registry.getSpec<CD>(specName) ?: run {
                logger?.warn { "Specification '$specName' for child '$name' not found in the component registry." }
                return null
            }

            val lifecycleConfigBuilder = DeviceLifecycleConfigBuilder()

            meta["config"]?.let { configMeta ->
                configMeta["lifecycleMode"]?.string?.let { modeStr ->
                    try {
                        lifecycleConfigBuilder.lifecycleMode = LifecycleMode.valueOf(modeStr.uppercase())
                    } catch (_: IllegalArgumentException) {
                        logger?.warn { "Invalid 'lifecycleMode' value '$modeStr' for child '$name'. Using default." }
                    }
                }
                configMeta["messageBuffer"]?.int?.let { lifecycleConfigBuilder.messageBuffer = it }
                ParsingUtils.parseDurationOrNull(configMeta["startDelay"].string)?.let { lifecycleConfigBuilder.startDelay = it }
                ParsingUtils.parseDurationOrNull(configMeta["startTimeout"].string)?.let { lifecycleConfigBuilder.startTimeout = it }
                ParsingUtils.parseDurationOrNull(configMeta["stopTimeout"].string)?.let { lifecycleConfigBuilder.stopTimeout = it }

                configMeta["onError"]?.string?.let { errorHandlerStr ->
                    try {
                        lifecycleConfigBuilder.onError = ChildDeviceErrorHandler.valueOf(errorHandlerStr.uppercase())
                    } catch (_: IllegalArgumentException) {
                        logger?.warn { "Invalid 'onError' value '$errorHandlerStr' for child '$name'. Using default." }
                    }
                }
                // TODO: Add parsing for RestartPolicy from meta.
            }

            val deviceInstanceMeta = meta["meta"]

            return object : ChildComponentConfig<CD> {
                override val spec: CompositeControlComponentSpec<CD> = spec
                override val config: DeviceLifecycleConfig = lifecycleConfigBuilder.build()
                override val meta: Meta? = deviceInstanceMeta
                override val name: Name = name
            }
        }

        /**
         * Creates a [ChildComponentConfigBuilder] for fluently constructing a [ChildComponentConfig].
         *
         * @param CD The type of the child's [ConfigurableCompositeControlComponent].
         * @param spec The [CompositeControlComponentSpec] for the child.
         * @param name The [Name] for this child component.
         * @return A new [ChildComponentConfigBuilder] instance.
         */
        public fun <CD : ConfigurableCompositeControlComponent<CD>> builder(
            spec: CompositeControlComponentSpec<CD>,
            name: Name
        ): ChildComponentConfigBuilder<CD> = ChildComponentConfigBuilder(spec, name)
    }
}

/**
 * Builder class for creating [ChildComponentConfig] instances with a fluent API.
 *
 * @param CD The type of the child's [ConfigurableCompositeControlComponent].
 * @property spec The [CompositeControlComponentSpec] for the child component.
 * @property name The [Name] for this child component.
 */
public class ChildComponentConfigBuilder<CD : ConfigurableCompositeControlComponent<CD>>(
    private val spec: CompositeControlComponentSpec<CD>,
    private val name: Name
) {
    private var lifecycleConfigBuilder = DeviceLifecycleConfigBuilder()
    private var meta: Meta? = null

    /**
     * Sets the entire [DeviceLifecycleConfig] for the child component.
     * This replaces any previous lifecycle configurations set via the builder.
     */
    public fun withLifecycleConfig(config: DeviceLifecycleConfig): ChildComponentConfigBuilder<CD> = apply {
        lifecycleConfigBuilder = DeviceLifecycleConfigBuilder().apply {
            lifecycleMode = config.lifecycleMode
            messageBuffer = config.messageBuffer
            startDelay = config.startDelay
            startTimeout = config.startTimeout
            stopTimeout = config.stopTimeout
            coroutineScope = config.coroutineScope
            dispatcher = config.dispatcher
            onError = config.onError
            restartPolicy = config.restartPolicy
        }
    }

    /**
     * Configures the [DeviceLifecycleConfig] for the child component using a lambda
     * applied to a [DeviceLifecycleConfigBuilder].
     */
    public fun withLifecycleConfigBuilder(block: DeviceLifecycleConfigBuilder.() -> Unit): ChildComponentConfigBuilder<CD> = apply {
        lifecycleConfigBuilder.apply(block)
    }

    /**
     * Sets the optional [Meta] for the child component instance.
     */
    public fun withMeta(meta: Meta?): ChildComponentConfigBuilder<CD> = apply {
        this.meta = meta
    }

    /**
     * Sets the optional [Meta] for the child component instance using a builder lambda.
     */
    public fun withMeta(block: MutableMeta.() -> Unit): ChildComponentConfigBuilder<CD> = apply {
        this.meta = Meta(block)
    }

    /**
     * Builds and returns the [ChildComponentConfig] instance.
     */
    public fun build(): ChildComponentConfig<CD> = object : ChildComponentConfig<CD> {
        override val spec: CompositeControlComponentSpec<CD> = this@ChildComponentConfigBuilder.spec
        override val config: DeviceLifecycleConfig = lifecycleConfigBuilder.build()
        override val meta: Meta? = this@ChildComponentConfigBuilder.meta
        override val name: Name = this@ChildComponentConfigBuilder.name
    }
}