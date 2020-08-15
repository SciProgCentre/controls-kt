package hep.dataforge.control.serial

import hep.dataforge.control.ports.Port
import jssc.SerialPort.*
import jssc.SerialPortEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import mu.KLogger
import mu.KotlinLogging
import kotlin.coroutines.coroutineContext
import jssc.SerialPort as JSSCPort

/**
 * COM/USB port
 */
class SerialPort private constructor(scope: CoroutineScope, val jssc: JSSCPort) : Port(scope) {
    override val logger: KLogger = KotlinLogging.logger("port[${jssc.portName}]")

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
    fun clearPort() {
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

    companion object {

        /**
         * Construct ComPort with given parameters
         */
        suspend fun open(
            portName: String,
            baudRate: Int = BAUDRATE_9600,
            dataBits: Int = DATABITS_8,
            stopBits: Int = STOPBITS_1,
            parity: Int = PARITY_NONE
        ): SerialPort {
            val jssc = JSSCPort(portName).apply {
                openPort()
                setParams(baudRate, dataBits, stopBits, parity)
            }
            val scope = CoroutineScope(SupervisorJob(coroutineContext[Job]))
            return SerialPort(scope, jssc)
        }
    }
}