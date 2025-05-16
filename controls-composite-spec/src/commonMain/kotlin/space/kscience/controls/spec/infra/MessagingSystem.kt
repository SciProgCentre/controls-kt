package space.kscience.controls.spec.infra

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import space.kscience.controls.api.*
import space.kscience.controls.spec.config.MessageBusConfig
import space.kscience.controls.api.DeviceException
import space.kscience.controls.spec.utils.TimeSource
import space.kscience.controls.spec.utils.SystemTimeSource as DefaultTimeSource
import space.kscience.controls.spec.utils.timeSourceOrDefault
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.Logger
import space.kscience.dataforge.context.error
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName
import kotlin.time.Duration

/**
 * Extension property to get message type from `@SerialName` annotation.
 */
@OptIn(ExperimentalSerializationApi::class)
public val Message.messageType: String
    get() = Message.serializer().descriptor.serialName

/**
 * Factory for creating common [Message] types.
 */
public class MessageFactory(private val timeSource: TimeSource) {
    public fun deviceLog(message: String, sourceDevice: Name, data: Meta? = null): DeviceLogMessage =
        DeviceLogMessage(message, data, sourceDevice, time = timeSource.now())

    public fun systemLog(message: String, component: String, details: Map<String, String> = emptyMap()): SystemLogMessage =
        SystemLogMessage(message, component, details = details, time = timeSource.now())

    public fun metricValue(name: String, value: Double, sourceDevice: Name = MessageBusConfig.Factory.Defaults.METRICS_DEFAULT_SOURCE_DEVICE_NAME, tags: Map<String, String> = emptyMap()): MetricMessage.MetricValueMessage =
        MetricMessage.MetricValueMessage(name, value, sourceDevice, tags, time = timeSource.now())

    public fun metricCounter(name: String, increment: Double = 1.0, sourceDevice: Name = MessageBusConfig.Factory.Defaults.METRICS_DEFAULT_SOURCE_DEVICE_NAME, tags: Map<String, String> = emptyMap()): MetricMessage.MetricCounterMessage =
        MetricMessage.MetricCounterMessage(name, increment, tags, sourceDevice, time = timeSource.now())

    public fun metricDuration(name: String, duration: Duration, sourceDevice: Name = MessageBusConfig.Factory.Defaults.METRICS_DEFAULT_SOURCE_DEVICE_NAME, tags: Map<String, String> = emptyMap()): MetricMessage.MetricDurationMessage =
        MetricMessage.MetricDurationMessage(name, duration.inWholeMilliseconds, tags, sourceDevice, time = timeSource.now())

    public fun deviceAdded(deviceName: String): DeviceStateMessage.DeviceStateAddedMessage =
        DeviceStateMessage.DeviceStateAddedMessage(deviceName, time = timeSource.now())

    public fun deviceStarted(deviceName: String): DeviceStateMessage.DeviceStateStartedMessage =
        DeviceStateMessage.DeviceStateStartedMessage(deviceName, time = timeSource.now())

    public fun deviceStopped(deviceName: String): DeviceStateMessage.DeviceStateStoppedMessage =
        DeviceStateMessage.DeviceStateStoppedMessage(deviceName, time = timeSource.now())

    public fun deviceRemoved(deviceName: String): DeviceStateMessage.DeviceStateRemovedMessage =
        DeviceStateMessage.DeviceStateRemovedMessage(deviceName, time = timeSource.now())

    public fun deviceFailed(deviceName: String, failure: SerializableDeviceFailure): DeviceStateMessage.DeviceStateFailedMessage =
        DeviceStateMessage.DeviceStateFailedMessage(deviceName, failure, time = timeSource.now())

    public fun deviceDetached(deviceName: String): DeviceStateMessage.DeviceStateDetachedMessage =
        DeviceStateMessage.DeviceStateDetachedMessage(deviceName, time = timeSource.now())
}

/**
 * Extension for creating [DeviceLogMessage] from a [Device].
 */
public fun Device.log(
    message: String,
    data: Meta? = null,
    timeSource: TimeSource = (this as? ContextAware)?.context?.timeSourceOrDefault ?: DefaultTimeSource
): DeviceLogMessage = DeviceLogMessage(message, data, this.id.asName(), time = timeSource.now())

/**
 * Filter for messages on the [MessageBus].
 */
@Serializable
public data class MessageFilter(
    val messageType: Collection<String>? = null,
    val sourceDevice: Collection<Name>? = null,
    val targetDevice: Collection<Name?>? = null,
) {
    public fun accepts(message: Message): Boolean =
        (messageType == null || messageType.contains(message.messageType)) &&
                (sourceDevice == null || sourceDevice.contains(message.sourceDevice)) &&
                (targetDevice == null || targetDevice.contains(message.targetDevice))

    public class Builder {
        private val messageTypes = mutableSetOf<String>()
        private val sourceDevices = mutableSetOf<Name>()
        private val targetDevices = mutableSetOf<Name?>()

        public fun messageType(type: String): Builder = apply { messageTypes.add(type) }
        public fun messageTypes(types: Collection<String>): Builder = apply { messageTypes.addAll(types) }
        public fun sourceDevice(device: Name): Builder = apply { sourceDevices.add(device) }
        public fun sourceDevice(deviceName: String): Builder = apply { sourceDevices.add(deviceName.parseAsName()) }
        public fun sourceDevices(devices: Collection<Name>): Builder = apply { sourceDevices.addAll(devices) }
        public fun targetDevice(device: Name?): Builder = apply { targetDevices.add(device) }
        public fun targetDevice(deviceName: String?): Builder = apply { targetDevices.add(deviceName?.parseAsName()) }
        public fun targetDevices(devices: Collection<Name?>): Builder = apply { targetDevices.addAll(devices) }

        public fun build(): MessageFilter = MessageFilter(
            messageTypes.ifEmpty { null },
            sourceDevices.ifEmpty { null },
            targetDevices.ifEmpty { null }
        )
    }
    public companion object {
        public val ALL: MessageFilter = MessageFilter()
        public fun builder(): Builder = Builder()
    }
}

/**
 * System for sending and receiving messages via a [MessageBus].
 */
public class MessagingSystem(
    public val messageBus: MessageBus,
    private val logger: Logger,
    private val timeSource: TimeSource = DefaultTimeSource,
    public val messageFactory: MessageFactory = MessageFactory(timeSource)
) {
    public suspend fun publish(message: Message) {
        try {
            messageBus.publish(message)
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish message (type ${message.messageType} from ${message.sourceDevice}): ${e.message}" }
        }
    }

    public inline fun <reified T : Message> getMessageFlow(filter: MessageFilter = MessageFilter.ALL): Flow<T> =
        messageBus.subscribe(filter).filterIsInstance<T>()

    public fun getDeviceLogMessages(): Flow<DeviceLogMessage> = getMessageFlow()
    public fun getSystemLogMessages(): Flow<SystemLogMessage> = getMessageFlow()
    public fun getDeviceStateMessages(): Flow<DeviceStateMessage> = getMessageFlow()
    public fun getTransactionMessages(): Flow<TransactionMessage> = getMessageFlow()
    public fun getMetricMessages(): Flow<MetricMessage> = getMessageFlow()
    public fun getDeviceFailureMessages(): Flow<DeviceFailureMessage> = getMessageFlow()
    public fun getAllMessages(): Flow<Message> = messageBus.subscribe(MessageFilter.ALL)

    public suspend fun logDevice(message: String, sourceDevice: Name, data: Meta? = null): Unit =
        publish(messageFactory.deviceLog(message, sourceDevice, data))

    public suspend fun logSystem(message: String, component: String, details: Map<String, String> = emptyMap()): Unit =
        publish(messageFactory.systemLog(message, component, details))

    public suspend fun recordMetricValue(name: String, value: Double, sourceDevice: Name = MessageBusConfig.Factory.Defaults.METRICS_DEFAULT_SOURCE_DEVICE_NAME, tags: Map<String, String> = emptyMap()): Unit =
        publish(messageFactory.metricValue(name, value, sourceDevice, tags))

    public suspend fun incrementCounter(name: String, increment: Double = 1.0, sourceDevice: Name = MessageBusConfig.Factory.Defaults.METRICS_DEFAULT_SOURCE_DEVICE_NAME, tags: Map<String, String> = emptyMap()): Unit =
        publish(messageFactory.metricCounter(name, increment, sourceDevice, tags))

    public suspend fun recordDuration(name: String, duration: Duration, sourceDevice: Name = MessageBusConfig.Factory.Defaults.METRICS_DEFAULT_SOURCE_DEVICE_NAME, tags: Map<String, String> = emptyMap()): Unit =
        publish(messageFactory.metricDuration(name, duration, sourceDevice, tags))

    public suspend fun reportDeviceFailure(failure: DeviceException, sourceDevice: Name?): Unit =
        publish(DeviceFailureMessage(failure.toSerializableFailure(), sourceDevice, time = timeSource.now()))

    public suspend fun reportDeviceFailure(failure: SerializableDeviceFailure, sourceDevice: Name?): Unit =
        publish(DeviceFailureMessage(failure, sourceDevice, time = timeSource.now()))
}