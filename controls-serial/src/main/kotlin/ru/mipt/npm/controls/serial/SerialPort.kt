package ru.mipt.npm.controls.serial

import jssc.SerialPort.*
import jssc.SerialPortEventListener
import ru.mipt.npm.controls.ports.AbstractPort
import ru.mipt.npm.controls.ports.Port
import ru.mipt.npm.controls.ports.PortFactory
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import kotlin.coroutines.CoroutineContext
import jssc.SerialPort as JSSCPort

/**
 * COM/USB port
 */
public class SerialPort private constructor(
    context: Context,
    private val jssc: JSSCPort,
    coroutineContext: CoroutineContext = context.coroutineContext,
) : AbstractPort(context, coroutineContext) {

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