package space.kscience.controls.serial

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.ports.AbstractAsynchronousPort
import space.kscience.controls.ports.AsynchronousPort
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import kotlin.coroutines.CoroutineContext

/**
 * A port based on JSerialComm
 */
public class AsynchronousSerialPort(
    context: Context,
    meta: Meta,
    private val comPort: SerialPort,
    coroutineContext: CoroutineContext = context.coroutineContext,
) : AbstractAsynchronousPort(context, meta, coroutineContext) {

    override fun toString(): String = "port[${comPort.descriptivePortName}]"

    private val serialPortListener = object : SerialPortDataListener {
        override fun getListeningEvents(): Int =
            SerialPort.LISTENING_EVENT_DATA_RECEIVED and SerialPort.LISTENING_EVENT_DATA_AVAILABLE

        override fun serialEvent(event: SerialPortEvent) {
            when (event.eventType) {
                SerialPort.LISTENING_EVENT_DATA_RECEIVED -> {
                    scope.launch { receive(event.receivedData) }
                }

                SerialPort.LISTENING_EVENT_DATA_AVAILABLE -> {
                    scope.launch(Dispatchers.IO) {
                        val available = comPort.bytesAvailable()
                        if (available > 0) {
                            val buffer = ByteArray(available)
                            comPort.readBytes(buffer, available)
                            receive(buffer)
                        }
                    }
                }
            }
        }
    }

    override fun onOpen() {
        comPort.openPort()
        comPort.addDataListener(serialPortListener)
    }

    override val lifecycleState: LifecycleState
        get() = if(comPort.isOpen) LifecycleState.STARTED else LifecycleState.STOPPED


    override suspend fun write(data: ByteArray) {
        comPort.writeBytes(data, data.size)
    }

    override suspend fun stop() {
        comPort.removeDataListener()
        if (comPort.isOpen) {
            comPort.closePort()
        }
        super.stop()
    }

    public companion object : Factory<AsynchronousPort> {

        public fun build(
            context: Context,
            portName: String,
            baudRate: Int = 9600,
            dataBits: Int = 8,
            stopBits: Int = SerialPort.ONE_STOP_BIT,
            parity: Int = SerialPort.NO_PARITY,
            coroutineContext: CoroutineContext = context.coroutineContext,
            additionalConfig: SerialPort.() -> Unit = {},
        ): AsynchronousSerialPort {
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
            return AsynchronousSerialPort(context, meta, serialPort, coroutineContext)
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
            coroutineContext: CoroutineContext = context.coroutineContext,
            additionalConfig: SerialPort.() -> Unit = {},
        ): AsynchronousSerialPort = build(
            context = context,
            portName = portName,
            baudRate = baudRate,
            dataBits = dataBits,
            stopBits = stopBits,
            parity = parity,
            coroutineContext = coroutineContext,
            additionalConfig = additionalConfig
        ).apply { start() }


        override fun build(context: Context, meta: Meta): AsynchronousPort {
            val name by meta.string { error("Serial port name not defined") }
            val baudRate by meta.int(9600)
            val dataBits by meta.int(8)
            val stopBits by meta.int(SerialPort.ONE_STOP_BIT)
            val parity by meta.int(SerialPort.NO_PARITY)
            return build(context, name, baudRate, dataBits, stopBits, parity)
        }
    }

}