package space.kscience.controls.spec.config

import kotlinx.serialization.Serializable
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName

/**
 * Configuration for message bus and related messaging parameters.
 *
 * @property inMemoryMessageBusBufferCapacity Buffer size for the in-memory message bus.
 * @property deviceMessageFlowBufferCapacity Buffer size for individual device message flows.
 * @property defaultMagixSourceEndpoint Default Magix endpoint identifier for this hub instance.
 * @property metricsDefaultSourceDeviceName Default source device name for metrics published by the hub itself.
 */
@Serializable
public data class MessageBusConfig(
    public val inMemoryMessageBusBufferCapacity: Int,
    public val deviceMessageFlowBufferCapacity: Int,
    public val defaultMagixSourceEndpoint: String,
    public val metricsDefaultSourceDeviceName: Name
) {
    /**
     * Converts this configuration object to a [Meta] representation.
     */
    public fun toMeta(): Meta = Meta {
        "inMemoryMessageBusBufferCapacity" put inMemoryMessageBusBufferCapacity
        "deviceMessageFlowBufferCapacity" put deviceMessageFlowBufferCapacity
        "defaultMagixSourceEndpoint" put defaultMagixSourceEndpoint
        "metricsDefaultSourceDeviceName" put metricsDefaultSourceDeviceName.toString()
    }

    public companion object Factory {
        public object Defaults {
            public const val IN_MEMORY_MESSAGE_BUS_BUFFER_CAPACITY: Int = 64
            public const val DEVICE_MESSAGE_FLOW_BUFFER_CAPACITY: Int = 1000
            public const val DEFAULT_MAGIX_SOURCE_ENDPOINT: String = "device.hub"
            public val METRICS_DEFAULT_SOURCE_DEVICE_NAME: Name = "metrics".asName()
        }

        /**
         * Creates a [MessageBusConfig] instance from a [Meta] object.
         */
        public fun fromMeta(meta: Meta): MessageBusConfig = MessageBusConfig(
            inMemoryMessageBusBufferCapacity = meta["inMemoryMessageBusBufferCapacity"].int ?: Defaults.IN_MEMORY_MESSAGE_BUS_BUFFER_CAPACITY,
            deviceMessageFlowBufferCapacity = meta["deviceMessageFlowBufferCapacity"].int ?: Defaults.DEVICE_MESSAGE_FLOW_BUFFER_CAPACITY,
            defaultMagixSourceEndpoint = meta["defaultMagixSourceEndpoint"].string ?: Defaults.DEFAULT_MAGIX_SOURCE_ENDPOINT,
            metricsDefaultSourceDeviceName = meta["metricsDefaultSourceDeviceName"]?.string?.parseAsName() ?: Defaults.METRICS_DEFAULT_SOURCE_DEVICE_NAME
        )
    }
}

/**
 * Builder for [MessageBusConfig].
 */
public class MessageBusConfigBuilder {
    public var inMemoryMessageBusBufferCapacity: Int = MessageBusConfig.Factory.Defaults.IN_MEMORY_MESSAGE_BUS_BUFFER_CAPACITY
    public var deviceMessageFlowBufferCapacity: Int = MessageBusConfig.Factory.Defaults.DEVICE_MESSAGE_FLOW_BUFFER_CAPACITY
    public var defaultMagixSourceEndpoint: String = MessageBusConfig.Factory.Defaults.DEFAULT_MAGIX_SOURCE_ENDPOINT
    public var metricsDefaultSourceDeviceName: Name = MessageBusConfig.Factory.Defaults.METRICS_DEFAULT_SOURCE_DEVICE_NAME

    public fun build(): MessageBusConfig = MessageBusConfig(
        inMemoryMessageBusBufferCapacity,
        deviceMessageFlowBufferCapacity,
        defaultMagixSourceEndpoint,
        metricsDefaultSourceDeviceName
    )
}

/**
 * DSL function to create [MessageBusConfig].
 */
public inline fun messageBusConfig(block: MessageBusConfigBuilder.() -> Unit = {}): MessageBusConfig =
    MessageBusConfigBuilder().apply(block).build()