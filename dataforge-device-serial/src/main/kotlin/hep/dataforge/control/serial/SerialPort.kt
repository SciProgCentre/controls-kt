package hep.dataforge.control.serial

import hep.dataforge.control.ports.AbstractPort
import jssc.SerialPort.*
import jssc.SerialPortEventListener
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import jssc.SerialPort as JSSCPort

/**
 * COM/USB port
 */
public class SerialPort private constructor(parentContext: CoroutineContext, private val jssc: JSSCPort) :
    AbstractPort(parentContext) {

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

    public companion object {

        /**
         * Construct ComPort with given parameters
         */
        public suspend fun open(
            portName: String,
            baudRate: Int = BAUDRATE_9600,
            dataBits: Int = DATABITS_8,
            stopBits: Int = STOPBITS_1,
            parity: Int = PARITY_NONE,
        ): SerialPort {
            val jssc = JSSCPort(portName).apply {
                openPort()
                setParams(baudRate, dataBits, stopBits, parity)
            }
            return SerialPort(coroutineContext, jssc)
        }
    }
}