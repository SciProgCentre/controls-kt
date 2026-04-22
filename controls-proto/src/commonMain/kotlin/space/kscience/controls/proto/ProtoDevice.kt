package space.kscience.controls.proto

import space.kscience.controls.ports.AsynchronousPort
import space.kscience.controls.ports.Ports
import space.kscience.controls.ports.send
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.IOFormat
import space.kscience.dataforge.io.asBinary
import space.kscience.dataforge.io.toByteArray
import space.kscience.dataforge.meta.Meta
import kotlinx.io.Buffer
import space.kscience.dataforge.meta.get
import kotlinx.coroutines.flow.first
import pace.kscience.dataforge.io.proto.ProtoEnvelopeFormat
import pace.kscience.dataforge.io.proto.ProtoMetaFormat

/**
 * A sample device that communicates using ProtoMeta messages.
 */
public open class ProtoDevice(
    context: Context,
    meta: Meta,
    private val format: IOFormat<Meta> = ProtoMetaFormat
) : DeviceBySpec<ProtoDevice>(ProtoDevice, context, meta) {

    public data class Response(
        val meta: Meta,
        val data: ByteArray?,
        val packetSize: Int,
    )

    private fun encodePacket(requestMeta: Meta, data: ByteArray? = null): ByteArray {
        val envelope = Envelope(requestMeta, data?.asBinary())
        return space.kscience.dataforge.io.ByteArray {
            ProtoEnvelopeFormat.writeTo(this, envelope)
        }
    }

    public fun packetSize(requestMeta: Meta, data: ByteArray? = null): Int = encodePacket(requestMeta, data).size
    
    public val port: AsynchronousPort by lazy {
        val ports = context.request(Ports)
        ports.buildAsynchronousPort(meta["port"] ?: error("Port is not defined in device configuration"))
    }

    public suspend fun send(requestMeta: Meta, data: ByteArray? = null) {
        val packet = encodePacket(requestMeta, data)
        port.send(packet)
    }

    public suspend fun requestWithData(requestMeta: Meta, data: ByteArray? = null): Response {
        send(requestMeta, data)

        val responseBytes = port.subscribe().first()
        val envelope = ProtoEnvelopeFormat.readFrom(Buffer().apply { write(responseBytes) })

        return Response(
            meta = envelope.meta,
            data = envelope.data?.toByteArray(),
            packetSize = responseBytes.size,
        )
    }

    public suspend fun request(requestMeta: Meta, data: ByteArray? = null): Meta {
        return requestWithData(requestMeta, data).meta
    }



    public companion object : DeviceSpec<ProtoDevice>() {
        override suspend fun ProtoDevice.onOpen() {
            port.start()
        }

        override suspend fun ProtoDevice.onClose() {
            port.stop()
        }
    }
}
