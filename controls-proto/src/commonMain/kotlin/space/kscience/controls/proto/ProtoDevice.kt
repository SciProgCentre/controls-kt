package space.kscience.controls.proto

import space.kscience.controls.api.Device
import space.kscience.controls.ports.Ports
import space.kscience.controls.ports.AsynchronousPort
import space.kscience.controls.ports.send
import space.kscience.dataforge.context.request
import space.kscience.controls.ports.withDelimiter
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.io.IOFormat
import space.kscience.dataforge.meta.Meta
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import space.kscience.dataforge.io.proto.ProtoMeta
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import space.kscience.dataforge.io.*
import kotlin.time.Duration.Companion.milliseconds

import pace.kscience.dataforge.io.proto.ProtoEnvelopeFormat
import pace.kscience.dataforge.io.proto.ProtoMetaFormat

/**
 * A sample device that communicates using ProtoMeta messages.
 */
open class ProtoDevice(
    context: Context,
    meta: Meta,
    private val format: IOFormat<Meta> = ProtoMetaFormat
) : DeviceBySpec<ProtoDevice>(ProtoDevice, context, meta) {
    
    public val port: AsynchronousPort by lazy {
        val ports = context.request(Ports)
        ports.buildAsynchronousPort(meta["port"] ?: error("Port is not defined in device configuration"))
    }

    public suspend fun send(requestMeta: Meta, data: ByteArray? = null) {
        val envelope = Envelope(requestMeta, data?.asBinary())
        val packet = space.kscience.dataforge.io.ByteArray {
            ProtoEnvelopeFormat.writeTo(this, envelope)
        }
        port.send(packet)
    }

    public suspend fun request(requestMeta: Meta, data: ByteArray? = null): Meta {
        send(requestMeta, data)

        val responseBytes = port.subscribe().first()
        val envelope = ProtoEnvelopeFormat.readFrom(Buffer().apply { write(responseBytes) })
        
        return envelope.meta
    }



    companion object : DeviceSpec<ProtoDevice>(){
        override suspend fun ProtoDevice.onOpen() {
            port.start()
        }

        override suspend fun ProtoDevice.onClose() {
            port.stop()
        }
    }
}
