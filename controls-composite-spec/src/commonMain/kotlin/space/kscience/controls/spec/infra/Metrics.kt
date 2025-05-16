package space.kscience.controls.spec.infra

import kotlinx.atomicfu.atomic
import kotlinx.datetime.Instant
import space.kscience.controls.api.MetricMessage
import space.kscience.controls.api.MetricMessage.*
import space.kscience.controls.spec.config.MessageBusConfig
import space.kscience.controls.spec.utils.TimeSource
import space.kscience.dataforge.context.Logger
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.names.Name
import kotlin.time.Duration

/**
 * Backend interface for publishing metrics.
 */
public interface MetricsBackend : AutoCloseable {
    public suspend fun publish(message: MetricMessage)
    override fun close() {}
}

/**
 * A [MetricsBackend] sending metrics via [MessageBus].
 */
public class MessageBusMetricsBackend(private val messageBus: MessageBus) : MetricsBackend {
    override suspend fun publish(message: MetricMessage) { messageBus.publish(message) }
}

/**
 * Interface for publishing various types of metrics.
 */
public interface MetricsPublisher : AutoCloseable {
    public suspend fun publishMetric(name: String, value: Double, tags: Map<String, String> = emptyMap())
    public suspend fun recordDuration(name: String, duration: Duration, tags: Map<String, String> = emptyMap())
    public suspend fun incrementCounter(name: String, amount: Double = 1.0, tags: Map<String, String> = emptyMap())
    public suspend fun recordGauge(name: String, value: Double, tags: Map<String, String> = emptyMap())
    public suspend fun recordDistribution(name: String, value: Double, tags: Map<String, String> = emptyMap())
    public suspend fun recordHistogramBucket(name: String, value: Double, bucket: String, tags: Map<String, String> = emptyMap())
    override fun close()
}

/**
 * Default implementation of [MetricsPublisher].
 */
public class MetricsPublisherImpl(
    private val backend: MetricsBackend,
    private val sourceDeviceName: Name = MessageBusConfig.Factory.Defaults.METRICS_DEFAULT_SOURCE_DEVICE_NAME,
    private val logger: Logger,
    private val timeSource: TimeSource
) : MetricsPublisher {
    private val isActive = atomic(true)

    private suspend fun tryPublish(messageCreator: (Instant) -> MetricMessage) {
        if (!isActive.value) {
            logger.warn { "MetricsPublisher for '$sourceDeviceName' is closed. Metric not published." }
            return
        }
        val message = messageCreator(timeSource.now())
        try {
            backend.publish(message)
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish metric '${message.metricName}' from '$sourceDeviceName'." }
        }
    }

    override suspend fun publishMetric(name: String, value: Double, tags: Map<String, String>): Unit =
        tryPublish { MetricValueMessage(name, value, sourceDeviceName, tags, time = it) }

    override suspend fun recordDuration(name: String, duration: Duration, tags: Map<String, String>): Unit =
        tryPublish { MetricDurationMessage(name, duration.inWholeMilliseconds, tags, sourceDeviceName, time = it) }

    override suspend fun incrementCounter(name: String, amount: Double, tags: Map<String, String>): Unit =
        tryPublish { MetricCounterMessage(name, amount, tags, sourceDeviceName, time = it) }

    override suspend fun recordGauge(name: String, value: Double, tags: Map<String, String>): Unit =
        tryPublish { MetricGaugeMessage(name, value, tags, sourceDeviceName, time = it) }

    override suspend fun recordDistribution(name: String, value: Double, tags: Map<String, String>): Unit =
        tryPublish { MetricDistributionMessage(name, value, tags, sourceDeviceName, time = it) }

    override suspend fun recordHistogramBucket(name: String, value: Double, bucket: String, tags: Map<String, String>): Unit =
        tryPublish {
            MetricValueMessage(name, value, sourceDeviceName, tags + ("bucket" to bucket), time = it)
        }

    override fun close() {
        if (isActive.compareAndSet(expect = true, update = false)) {
            try { backend.close() }
            catch (e: Exception) { logger.error(e) { "Error closing metrics backend for '$sourceDeviceName'." } }
            logger.info { "MetricsPublisher for '$sourceDeviceName' closed." }
        }
    }
    // TODO: adding support for high-frequency metrics.
}
