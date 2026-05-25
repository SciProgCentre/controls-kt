package space.kscience.controls.binary

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import space.kscience.controls.api.BinaryNotificationMessage
import space.kscience.controls.api.DeviceMessageSource
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.Name

/**
 * A class that acts as a producer of frames by subscribing to device messages and fetching relevant binary data.
 *
 * @constructor Creates an instance of [DeviceFrameProducer].
 * @param connection The [PeerConnection] interface used to receive binary data from devices.
 * @param messageSource The [DeviceMessageSource] providing a flow of device messages.
 * @param filterDeviceName A filter function used to determine if a device's name should be processed.
 * @param sourceByDeviceName A mapping function to resolve the source address string from a device's name.
 */
@DFExperimental
public class DeviceFrameProducer(
    public val scope: CoroutineScope,
    private val connection: PeerConnection,
    private val messageSource: DeviceMessageSource,
    private val filterDeviceName: (Name) -> Boolean,
    private val sourceByDeviceName: (Name) -> String = { it.toString() },
) : FrameProducer {

    private val _telemetry: MutableSharedFlow<FrameTelemetry> =
        MutableSharedFlow(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val telemetry: SharedFlow<FrameTelemetry> get() = _telemetry

    private val subscription = messageSource.messageFlow
        .filterIsInstance<BinaryNotificationMessage>()
        .filter { filterDeviceName(it.sourceDevice) }
        .mapNotNull { notification ->
            connection.receive(
                address = sourceByDeviceName(notification.sourceDevice),
                contentId = notification.contentId,
                requestMeta = notification.contentMeta["request"] ?: Meta.EMPTY,
            ).also {
                val telemetryEvent: FrameTelemetry = FrameTelemetry(
                    frameId = notification.contentId,
                    started = null,
                    finished = notification.time,
                    success = it != null,
                )

                _telemetry.emit(telemetryEvent)
            }
        }.shareIn(scope, SharingStarted.Eagerly)

    override fun subscribe(): Flow<Envelope> = subscription

}