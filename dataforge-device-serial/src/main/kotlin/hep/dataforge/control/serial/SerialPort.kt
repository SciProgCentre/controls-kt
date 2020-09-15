package hep.dataforge.control.serial

import hep.dataforge.context.Context
import hep.dataforge.control.ports.AbstractPort
import hep.dataforge.control.ports.Port
import hep.dataforge.control.ports.PortFactory
import hep.dataforge.meta.Meta
import hep.dataforge.meta.int
import hep.dataforge.meta.string
import jssc.SerialPort.*
import jssc.SerialPortEventListener
import kotlin.coroutines.CoroutineContext
import jssc.SerialPort as JSSCPort

/**
 * COM/USB port
 */
public class SerialPort private constructor(
    context: Context,
    private val jssc: JSSCPort,
    parentContext: CoroutineContext = context.coroutineContext,
) : AbstractPort(context, parentContext) {

    override fun toString(): String = "port[${jssc.portName}]"

    private val serialPortListener = SerialPortEventListener { event ->
        if (event.isRXCHAR) {
            val chars = event.eventValue
            val bytes = jssc.readBytes(chars)
            receive(bytes)
        }
    }

    init {
        jssc.addEventListener(serialPortListener)
    }

    /**
     * Clear current input and output buffers
     */
    internal fun clearPort() {
        jssc.purgePort(PURGE_RXCLEAR or PURGE_TXCLEAR)
    }

    override suspend fun write(data: ByteArray) {
        jssc.writeBytes(data)
    }

    @Throws(Exception::class)
    override fun close() {
        jssc.removeEventListener()
        clearPort()
        if (jssc.isOpened) {
            jssc.closePort()
        }
        super.close()
    }

    public companion object : PortFactory {

        /**
         * Construct ComPort with given parameters
         */
        public fun open(
            context: Context,
            portName: String,
            baudRate: Int = BAUDRATE_9600,
            dataBits: Int = DATABITS_8,
            stopBits: Int = STOPBITS_1,
            parity: Int = PARITY_NONE,
            coroutineContext: CoroutineContext = context.coroutineContext,
        ): SerialPort {
            val jssc = JSSCPort(portName).apply {
                openPort()
                setParams(baudRate, dataBits, stopBits, parity)
            }
            return SerialPort(context, jssc, coroutineContext)
        }

        override fun invoke(meta: Meta, context: Context): Port {
            val name by meta.string { error("Serial port name not defined") }
            val baudRate by meta.int(BAUDRATE_9600)
            val dataBits by meta.int(DATABITS_8)
            val stopBits by meta.int(STOPBITS_1)
            val parity by meta.int(PARITY_NONE)
            return open(context, name, baudRate, dataBits, stopBits, parity)
        }
    }
}