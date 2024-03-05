package space.kscience.controls.serial

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import kotlinx.coroutines.launch
import space.kscience.controls.ports.AbstractPort
import space.kscience.controls.ports.Port
import space.kscience.controls.ports.PortFactory
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import kotlin.coroutines.CoroutineContext

/**
 * A port based on JSerialComm
 */
public class JSerialCommPort(
    context: Context,
    private val comPort: SerialPort,
    coroutineContext: CoroutineContext = context.coroutineContext,
) : AbstractPort(context, coroutineContext) {

    override fun toString(): String = "port[${comPort.descriptivePortName}]"

    private val serialPortListener = object : SerialPortDataListener {
        override fun getListeningEvents(): Int = SerialPort.LISTENING_EVENT_DATA_AVAILABLE

        override fun serialEvent(event: SerialPortEvent) {
            if (event.eventType == SerialPort.LISTENING_EVENT_DATA_AVAILABLE && event.receivedData != null) {
                scope.launch { receive(event.receivedData) }
            }
        }
    }

    init {
        comPort.addDataListener(serialPortListener)
    }

    override suspend fun write(data: ByteArray) {
        comPort.writeBytes(data, data.size)
    }

    override fun close() {
        comPort.removeDataListener()
        if (comPort.isOpen) {
            comPort.closePort()
        }
        super.close()
    }

    public companion object : PortFactory {

        override val type: String = "com"


        /**
         * Construct ComPort with given parameters
         */
        public fun open(
            context: Context,
            portName: String,
            baudRate: Int = 9600,
            dataBits: Int = 8,
            stopBits: Int = SerialPort.ONE_STOP_BIT,
            parity: Int = SerialPort.NO_PARITY,
            coroutineContext: CoroutineContext = context.coroutineContext,
        ): JSerialCommPort {
            val serialPort = SerialPort.getCommPort(portName).apply {
                setComPortParameters(baudRate, dataBits, stopBits, parity)
                openPort()
            }
            return JSerialCommPort(context, serialPort, coroutineContext)
        }

        override fun build(context: Context, meta: Meta): Port {
            val name by meta.string { error("Serial port name not defined") }
            val baudRate by meta.int(9600)
            val dataBits by meta.int(8)
            val stopBits by meta.int(SerialPort.ONE_STOP_BIT)
            val parity by meta.int(SerialPort.NO_PARITY)
            return open(context, name, baudRate, dataBits, stopBits, parity)
        }
    }

}