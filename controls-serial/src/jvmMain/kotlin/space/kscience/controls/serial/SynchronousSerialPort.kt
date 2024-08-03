package space.kscience.controls.serial

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.ports.SynchronousPort
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string

/**
 * A port based on JSerialComm
 */
public class SynchronousSerialPort(
    override val context: Context,
    public val meta: Meta,
    private val comPort: SerialPort,
) : SynchronousPort {

    override fun toString(): String = "port[${comPort.descriptivePortName}]"


    override suspend fun start() {
        if (!comPort.isOpen) {
            comPort.openPort()
        }
    }

    override val lifecycleState: LifecycleState
        get() = if(comPort.isOpen) LifecycleState.STARTED else LifecycleState.STOPPED


    override suspend fun stop() {
        if (comPort.isOpen) {
            comPort.closePort()
        }
    }

    private val mutex = Mutex()

    override suspend fun <R> respond(
        request: ByteArray,
        transform: suspend Flow<ByteArray>.() -> R,
    ): R = mutex.withLock {
        comPort.flushIOBuffers()
        comPort.writeBytes(request, request.size)
        flow<ByteArray> {
            while (comPort.isOpen) {
                try {
                    val available = comPort.bytesAvailable()
                    if (available > 0) {
                        val buffer = ByteArray(available)
                        comPort.readBytes(buffer, available)
                        emit(buffer)
                    } else if (available < 0) break
                } catch (ex: Exception) {
                    logger.error(ex) { "Channel read error" }
                    delay(1000)
                }
            }
        }.transform()
    }

    override suspend fun respondFixedMessageSize(request: ByteArray, responseSize: Int): ByteArray = mutex.withLock {
        runInterruptible {
            comPort.flushIOBuffers()
            comPort.writeBytes(request, request.size)
            val buffer = ByteArray(responseSize)
            comPort.readBytes(buffer, responseSize)
            buffer
        }
    }

    public companion object : Factory<SynchronousPort> {

        public fun build(
            context: Context,
            portName: String,
            baudRate: Int = 9600,
            dataBits: Int = 8,
            stopBits: Int = SerialPort.ONE_STOP_BIT,
            parity: Int = SerialPort.NO_PARITY,
            additionalConfig: SerialPort.() -> Unit = {},
        ): SynchronousSerialPort {
            val serialPort = SerialPort.getCommPort(portName).apply {
                setComPortParameters(baudRate, dataBits, stopBits, parity)
                additionalConfig()
            }
            val meta = Meta {
                "name" put "com://$portName"
                "type" put "serial"
                "baudRate" put serialPort.baudRate
                "dataBits" put serialPort.numDataBits
                "stopBits" put serialPort.numStopBits
                "parity" put serialPort.parity
            }
            return SynchronousSerialPort(context, meta, serialPort)
        }


        /**
         * Construct ComPort with given parameters
         */
        public suspend fun start(
            context: Context,
            portName: String,
            baudRate: Int = 9600,
            dataBits: Int = 8,
            stopBits: Int = SerialPort.ONE_STOP_BIT,
            parity: Int = SerialPort.NO_PARITY,
            additionalConfig: SerialPort.() -> Unit = {},
        ): SynchronousSerialPort = build(
            context = context,
            portName = portName,
            baudRate = baudRate,
            dataBits = dataBits,
            stopBits = stopBits,
            parity = parity,
            additionalConfig = additionalConfig
        ).apply { start() }


        override fun build(context: Context, meta: Meta): SynchronousPort {
            val name by meta.string { error("Serial port name not defined") }
            val baudRate by meta.int(9600)
            val dataBits by meta.int(8)
            val stopBits by meta.int(SerialPort.ONE_STOP_BIT)
            val parity by meta.int(SerialPort.NO_PARITY)
            return build(context, name, baudRate, dataBits, stopBits, parity)
        }
    }

}