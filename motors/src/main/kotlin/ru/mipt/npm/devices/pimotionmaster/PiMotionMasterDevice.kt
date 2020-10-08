package ru.mipt.npm.devices.pimotionmaster

import hep.dataforge.context.Context
import hep.dataforge.control.api.DeviceHub
import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.control.base.*
import hep.dataforge.control.controllers.boolean
import hep.dataforge.control.controllers.double
import hep.dataforge.control.controllers.duration
import hep.dataforge.control.ports.Port
import hep.dataforge.control.ports.PortProxy
import hep.dataforge.control.ports.send
import hep.dataforge.control.ports.withDelimiter
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.asMetaItem
import hep.dataforge.names.NameToken
import hep.dataforge.values.Null
import hep.dataforge.values.asValue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration


public class PiMotionMasterDevice(
    context: Context,
    axes: List<String>,
    private val portFactory: suspend (MetaItem<*>?) -> Port,
) : DeviceBase(context), DeviceHub {

    override val scope: CoroutineScope = CoroutineScope(
        context.coroutineContext + SupervisorJob(context.coroutineContext[Job])
    )

    public val port: DeviceProperty by writingVirtual(Null) {
        info = "The port for TCP connector"
    }

    public val timeout: DeviceProperty by writingVirtual(Null) {
        info = "Timeout"
    }

    public var timeoutValue: Duration by timeout.duration()

    private val connector = PortProxy { portFactory(port.value) }

    private val mutex = Mutex()

    private suspend fun dispatchError(errorCode: Int) {
        logger.error { "Error code: $errorCode" }
        //TODO add error handling
    }

    private suspend fun sendCommandInternal(command: String, vararg arguments: String) {
        val joinedArguments = if (arguments.isEmpty()) {
            ""
        } else {
            arguments.joinToString(prefix = " ", separator = " ", postfix = "")
        }
        val stringToSend = "$command$joinedArguments\n"
        connector.send(stringToSend)
    }

    public suspend fun getErrorCode(): Int = mutex.withLock {
        withTimeout(timeoutValue) {
            sendCommandInternal("ERR?")
            val errorString = connector.receiving().withDelimiter("\n").first()
            errorString.toInt()
        }
    }


    /**
     * Send a synchronous request and receive a list of lines as a response
     */
    private suspend fun request(command: String, vararg arguments: String): List<String> = mutex.withLock {
        try {
            withTimeout(timeoutValue) {
                sendCommandInternal(command, *arguments)
                val phrases = connector.receiving().withDelimiter("\n")
                phrases.takeWhile { it.endsWith(" \n") }.toList() + phrases.first()
            }
        } catch (ex: Throwable) {
            logger.warn { "Error during PIMotionMaster request. Requesting error code." }
            val errorCode = getErrorCode()
            dispatchError(errorCode)
            logger.warn { "Error code $errorCode" }
            error("Error code $errorCode")
        }
    }

    private suspend fun requestAndParse(command: String, vararg arguments: String): Map<String, String> = buildMap {
        request(command, *arguments).forEach { line ->
            val (key, value) = line.split("=")
            put(key, value)
        }
    }

    /**
     * Send a synchronous command
     */
    private suspend fun send(command: String, vararg arguments: String) {
        mutex.withLock {
            withTimeout(timeoutValue) {
                sendCommandInternal(command, *arguments)
            }
        }
    }


    public val initialize: DeviceAction by acting {
        send("INI")
    }

    public val firmwareVersion: ReadOnlyDeviceProperty by readingString {
        request("VER?").first()
    }

    public val stop: DeviceAction by acting(
        descriptorBuilder = {
            info = "Stop all axis"
        },
        action = { send("STP") }
    )

    public inner class Axis(public val axisId: String) : DeviceBase(context) {
        override val scope: CoroutineScope get() = this@PiMotionMasterDevice.scope

        private suspend fun readAxisBoolean(command: String): Boolean =
            requestAndParse(command, axisId)[axisId]?.toIntOrNull()
                    ?: error("Malformed $command response. Should include integer value for $axisId") != 0

        private suspend fun writeAxisBoolean(command: String, value: Boolean): Boolean {
            val boolean = if (value) {
                "1"
            } else {
                "0"
            }
            send(command, axisId, boolean)
            return value
        }

        private fun axisBooleanProperty(command: String, descriptorBuilder: PropertyDescriptor.() -> Unit = {}) =
            writingBoolean<Axis>(
                getter = { readAxisBoolean("$command?") },
                setter = { _, newValue -> writeAxisBoolean(command, newValue) },
                descriptorBuilder = descriptorBuilder
            )

        private fun axisNumberProperty(command: String, descriptorBuilder: PropertyDescriptor.() -> Unit = {}) =
            writingDouble<Axis>(
                getter = {
                    requestAndParse("$command?", axisId)[axisId]?.toDoubleOrNull()
                        ?: error("Malformed $command response. Should include float value for $axisId")
                },
                setter = { _, newValue ->
                    send(command, axisId, newValue.toString())
                    newValue
                },
                descriptorBuilder = descriptorBuilder
            )

        public val enabled: DeviceProperty by axisBooleanProperty("EAX") {
            info = "Motor enable state."
        }

        public val halt: DeviceAction by acting {
            send("HLT", axisId)
        }

        public val targetPosition: DeviceProperty by axisNumberProperty("MOV") {
            info = """
                Sets a new absolute target position for the specified axis.
                Servo mode must be switched on for the commanded axis prior to using this command (closed-loop operation).
            """.trimIndent()
        }

        public val onTarget: ReadOnlyDeviceProperty by readingBoolean(
            descriptorBuilder = {
                info = "Queries the on-target state of the specified axis."
            },
            getter = {
                readAxisBoolean("ONT?")
            }
        )

        public val reference: ReadOnlyDeviceProperty by readingBoolean(
            descriptorBuilder = {
                info = "Get Referencing Result"
            },
            getter = {
                readAxisBoolean("FRF?")
            }
        )

        val moveToReference by acting {
            send("FRF", axisId)
        }

        public val position: DeviceProperty by axisNumberProperty("POS") {
            info = "The current axis position."
        }

        var positionValue by position.double()

        public val openLoopTarget: DeviceProperty by axisNumberProperty("OMA") {
            info = "Position for open-loop operation."
        }

        public val closedLoop: DeviceProperty by axisBooleanProperty("SVO") {
            info = "Servo closed loop mode"
        }

        var closedLoopValue by closedLoop.boolean()

        public val velocity: DeviceProperty by axisNumberProperty("VEL") {
            info = "Velocity value for closed-loop operation"
        }
    }

    val axisIds: ReadOnlyDeviceProperty by reading {
        request("SAI?").map { it.asValue() }.asValue().asMetaItem()
    }

    override val devices: Map<NameToken, Axis> = axes.associate { NameToken(it) to Axis(it) }

    /**
     * Name-friendly accessor for axis
     */
    val axes: Map<String, Axis> get() = devices.mapKeys { it.toString() }

}