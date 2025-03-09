package space.kscience.controls.pi

import com.pi4j.io.serial.Baud
import com.pi4j.io.serial.Serial
import com.pi4j.io.serial.SerialConfigBuilder
import com.pi4j.ktx.io.serial
import kotlinx.coroutines.*
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.ports.AbstractAsynchronousPort
import space.kscience.controls.ports.AsynchronousPort
import space.kscience.controls.ports.copyToArray
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.enum
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

public class AsynchronousPiPort(
    context: Context,
    meta: Meta,
    private val serial: Serial,
    coroutineContext: CoroutineContext = context.coroutineContext,
) : AbstractAsynchronousPort(context, meta, coroutineContext) {


    private var listenerJob: Job? = null
    override fun onOpen() {
        serial.open()
        listenerJob = this.scope.launch(Dispatchers.IO) {
            val buffer = ByteBuffer.allocate(1024)
            while (isActive) {
                try {
                    val num = serial.read(buffer)
                    if (num > 0) {
                        receive(buffer.copyToArray(num))
                    }
                    if (num < 0) cancel("The input channel is exhausted")
                } catch (ex: Exception) {
                    logger.error(ex) { "Channel read error" }
                    delay(1000)
                }
            }
        }
    }

    override suspend fun write(data: ByteArray): Unit = withContext(Dispatchers.IO) {
        serial.write(data)
    }


    override val lifecycleState: LifecycleState
        get() = if(listenerJob?.isActive == true) LifecycleState.STARTED else LifecycleState.STOPPED

    override suspend fun stop() {
        listenerJob?.cancel()
        serial.close()
    }

    public companion object : Factory<AsynchronousPort> {


        public fun build(
            context: Context,
            device: String,
            block: SerialConfigBuilder.() -> Unit,
        ): AsynchronousPiPort {
            val meta = Meta {
                "name" put "pi://$device"
                "type" put "serial"
            }
            val pi = context.request(PiPlugin)

            val serial = pi.piContext.serial(device, block)
            return AsynchronousPiPort(context, meta, serial)
        }

        public suspend fun start(
            context: Context,
            device: String,
            block: SerialConfigBuilder.() -> Unit,
        ): AsynchronousPiPort = build(context, device, block).apply { start() }

        override fun build(context: Context, meta: Meta): AsynchronousPort {
            val device: String = meta["device"].string ?: error("Device name not defined")
            val baudRate: Baud = meta["baudRate"].enum<Baud>() ?: Baud._9600
            val pi = context.request(PiPlugin)
            val serial = pi.piContext.serial(device) {
                baud8N1(baudRate)
            }
            return AsynchronousPiPort(context, meta, serial)
        }

    }
}

