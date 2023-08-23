package space.kscience.controls.pi

import com.pi4j.Pi4J
import com.pi4j.io.serial.Baud
import com.pi4j.io.serial.Serial
import com.pi4j.io.serial.SerialConfigBuilder
import com.pi4j.ktx.io.serial
import kotlinx.coroutines.*
import space.kscience.controls.ports.AbstractPort
import space.kscience.controls.ports.Port
import space.kscience.controls.ports.PortFactory
import space.kscience.controls.ports.toArray
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.enum
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

public class PiSerialPort(
    context: Context,
    coroutineContext: CoroutineContext = context.coroutineContext,
    public val serialBuilder: () -> Serial,
) : AbstractPort(context, coroutineContext) {

    private val serial: Serial by lazy { serialBuilder() }


    private val listenerJob = this.scope.launch(Dispatchers.IO) {
        val buffer = ByteBuffer.allocate(1024)
        while (isActive) {
            try {
                val num = serial.read(buffer)
                if (num > 0) {
                    receive(buffer.toArray(num))
                }
                if (num < 0) cancel("The input channel is exhausted")
            } catch (ex: Exception) {
                logger.error(ex) { "Channel read error" }
                delay(1000)
            }
        }
    }

    override suspend fun write(data: ByteArray): Unit = withContext(Dispatchers.IO) {
        serial.write(data)
    }

    override fun close() {
        listenerJob.cancel()
        serial.close()
    }

    public companion object : PortFactory {
        override val type: String get() = "pi"

        public fun open(context: Context, device: String, block: SerialConfigBuilder.() -> Unit): PiSerialPort =
            PiSerialPort(context) {
                Pi4J.newAutoContext().serial(device, block)
            }

        override fun build(context: Context, meta: Meta): Port = PiSerialPort(context) {
            val device: String = meta["device"].string ?: error("Device name not defined")
            val baudRate: Baud = meta["baudRate"].enum<Baud>() ?: Baud._9600
            Pi4J.newAutoContext().serial(device) {
                baud8N1(baudRate)
            }
        }

    }
}

