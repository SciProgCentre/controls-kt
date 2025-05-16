package space.kscience.controls.spec.config

import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import space.kscience.controls.spec.utils.ParsingUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Configuration for the [DeviceHubManager].
 *
 * @property messageBufferSize Default buffer size for device message flows if not specified per device.
 * @property defaultConcurrencyLevel Default concurrency level for the hub's dispatcher.
 * @property defaultStartTimeout Default timeout for starting devices if not specified in their [DeviceLifecycleConfig].
 * @property defaultStopTimeout Default timeout for stopping devices if not specified in their [DeviceLifecycleConfig].
 * @property resourceCleanupInterval Interval for running cleanup tasks (e.g., for circuit breakers, restart states).
 * @property resourceMaxIdleTime Maximum idle time for resources before they are considered for cleanup.
 * @property healthRestartConcurrency Concurrency limit for restarts triggered by health checks.
 * @property meta The raw [Meta] object from which this configuration was built, or an empty Meta.
 */
public class DeviceHubConfig(
    public val messageBufferSize: Int = MessageBusConfig.Factory.Defaults.DEVICE_MESSAGE_FLOW_BUFFER_CAPACITY,
    public val defaultConcurrencyLevel: Int = Defaults.CONCURRENCY_LEVEL,
    public val defaultStartTimeout: Duration = DeviceLifecycleConfig.Factory.Defaults.DEVICE_START_TIMEOUT,
    public val defaultStopTimeout: Duration = DeviceLifecycleConfig.Factory.Defaults.DEVICE_STOP_TIMEOUT,
    public val resourceCleanupInterval: Duration = Defaults.RESOURCE_CLEANUP_INTERVAL,
    public val resourceMaxIdleTime: Duration = Defaults.RESOURCE_MAX_IDLE_TIME,
    public val healthRestartConcurrency: Int = Defaults.HEALTH_CHECK_RESTART_CONCURRENCY,
    public val defaultActionExecutionTimeout: Duration = DeviceLifecycleConfig.Factory.Defaults.DEVICE_ACTION_EXECUTION_TIMEOUT,
    override val meta: Meta = Meta.EMPTY
) : AbstractPlugin() {
    override val tag: PluginTag get() = Factory.tag

    init {
        require(messageBufferSize > 0) { "Message buffer size must be positive." }
        require(defaultConcurrencyLevel > 0) { "Default concurrency level must be positive." }
        require(healthRestartConcurrency > 0) { "Health restart concurrency must be positive." }
        require(!resourceCleanupInterval.isNegative()) { "Resource cleanup interval must be non-negative." }
        require(!resourceMaxIdleTime.isNegative()) { "Resource max idle time must be non-negative." }
    }

    /**
     * Converts this configuration object to a [Meta] representation.
     */
    public override fun toMeta(): Meta = Meta {
        "messageBufferSize" put messageBufferSize
        "defaultConcurrencyLevel" put defaultConcurrencyLevel
        "defaultStartTimeout" put defaultStartTimeout.toString()
        "defaultStopTimeout" put defaultStopTimeout.toString()
        "resourceCleanupInterval" put resourceCleanupInterval.toString()
        "resourceMaxIdleTime" put resourceMaxIdleTime.toString()
        "healthRestartConcurrency" put healthRestartConcurrency
        "defaultActionExecutionTimeout" put defaultActionExecutionTimeout.toString()
        if (this@DeviceHubConfig.meta.items.isNotEmpty()) {
            "sourceMeta" put this@DeviceHubConfig.meta
        }
    }

    public companion object Factory : PluginFactory<DeviceHubConfig> {
        override val tag: PluginTag = PluginTag("controls.device.hub.config", PluginTag.DATAFORGE_GROUP)

        public object Defaults {
            public const val CONCURRENCY_LEVEL: Int = 4
            public const val HEALTH_CHECK_RESTART_CONCURRENCY: Int = 2
            public val RESOURCE_CLEANUP_INTERVAL: Duration = 15.minutes
            public val RESOURCE_MAX_IDLE_TIME: Duration = 60.minutes
        }

        /**
         * Builds a [DeviceHubConfig] from a [Context] and [Meta].
         * Values from [meta] override defaults.
         */
        override fun build(context: Context, meta: Meta): DeviceHubConfig = fromMeta(meta)

        /**
         * Creates a [DeviceHubConfig] instance from a [Meta] object.
         */
        public fun fromMeta(meta: Meta): DeviceHubConfig = DeviceHubConfig(
            meta["messageBufferSize"].int ?: MessageBusConfig.Factory.Defaults.DEVICE_MESSAGE_FLOW_BUFFER_CAPACITY,
            meta["defaultConcurrencyLevel"].int ?: Defaults.CONCURRENCY_LEVEL,
            meta["defaultStartTimeout"]?.string?.let { ParsingUtils.parseDurationOrNull(it) }
                ?: DeviceLifecycleConfig.Factory.Defaults.DEVICE_START_TIMEOUT,
            meta["defaultStopTimeout"]?.string?.let { ParsingUtils.parseDurationOrNull(it) }
                ?: DeviceLifecycleConfig.Factory.Defaults.DEVICE_STOP_TIMEOUT,
            meta["resourceCleanupInterval"]?.string?.let { ParsingUtils.parseDurationOrNull(it) }
                ?: Defaults.RESOURCE_CLEANUP_INTERVAL,
            meta["resourceMaxIdleTime"]?.string?.let { ParsingUtils.parseDurationOrNull(it) }
                ?: Defaults.RESOURCE_MAX_IDLE_TIME,
            meta["healthRestartConcurrency"].int ?: Defaults.HEALTH_CHECK_RESTART_CONCURRENCY,
            meta["defaultActionExecutionTimeout"]?.string?.let { ParsingUtils.parseDurationOrNull(it) }
                ?: DeviceLifecycleConfig.Factory.Defaults.DEVICE_ACTION_EXECUTION_TIMEOUT,
            meta
        )
    }
}