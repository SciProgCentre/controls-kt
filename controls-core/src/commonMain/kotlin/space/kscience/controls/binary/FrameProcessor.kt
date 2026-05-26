package space.kscience.controls.binary

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.dataID
import space.kscience.dataforge.meta.Meta
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A producer of binary frames
 */
public fun interface FrameProducer {
    public fun subscribe(): Flow<Envelope>
    public val telemetry: Flow<FrameTelemetry> get() = emptyFlow()
}

/**
 * A consumer of binary frames
 */
public fun interface FrameConsumer {
    public suspend fun send(frame: Envelope)
}

/**
 * A function that transforms a frame
 */
public fun interface FrameTransformer {
    public suspend fun transform(frame: Envelope): Envelope
}

/**
 * Telemetry event for frame processor
 *
 * @param frameId the unique id of the frame that was produced or processed
 * @param started the time when the frame was started to be processed
 * @param finished the time when the frame was finished to be processed
 * @param success true if the frame was processed successfully, false otherwise
 * @param meta additional meta information
 */
@Serializable
public data class FrameTelemetry(
    public val frameId: String?,
    public val started: Instant?,
    public val finished: Instant,
    public val success: Boolean,
    public val meta: Meta = Meta.EMPTY,
)

/**
 * A frame processor that transforms frames on flight
 */
public class FrameProcessor(
    public val scope: CoroutineScope,
    public val transformer: FrameTransformer,
    incomingBuffer: Int = Channel.RENDEZVOUS,
    outgoingReplay: Int = 1,
    public val clock: Clock = Clock.System,
) : FrameProducer, FrameConsumer {

    private val incoming: Channel<Envelope> = Channel(incomingBuffer)

    private val _telemetry: MutableSharedFlow<FrameTelemetry> = MutableSharedFlow()

    override val telemetry: SharedFlow<FrameTelemetry> = _telemetry

    private val _queueLength = MutableStateFlow(0)

    public val queueLength: StateFlow<Int> = _queueLength

    private val outgoing: MutableSharedFlow<Envelope> = MutableSharedFlow(
        replay = outgoingReplay,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun subscribe(): SharedFlow<Envelope> = outgoing

    override suspend fun send(frame: Envelope) {
        incoming.send(frame)
        _queueLength.value += 1
    }

    private val processingJob = scope.launch {
        for (frame in incoming) {
            val startTime = clock.now()
            try {
                val result = transformer.transform(frame)
                outgoing.emit(result)
                val endTime = clock.now()
                _telemetry.emit(
                    FrameTelemetry(
                        frameId = frame.dataID,
                        started = startTime,
                        finished = endTime,
                        success = true,
                    )
                )
            } catch (ex: Exception) {
                val endTime = clock.now()
                _telemetry.emit(
                    FrameTelemetry(
                        frameId = frame.dataID,
                        started = startTime,
                        finished = endTime,
                        success = false,
                        meta = Meta {
                            "error" put (ex::class.simpleName ?: "Exception")
                            ex.message?.let { "message" put it }
                        }
                    )
                )
            } finally {
                _queueLength.value -= 1
            }
        }
    }
}

/**
 * Subscribe a producer to a consumer
 */
public fun FrameConsumer.subscribe(scope: CoroutineScope, producer: FrameProducer): Job =
    producer.subscribe().onEach { send(it) }.launchIn(scope)

/**
 * Subscribe a processor to a consumer using processor scope for subscription
 */
public fun FrameProcessor.subscribe( producer: FrameProducer): Job = subscribe(scope, producer)
