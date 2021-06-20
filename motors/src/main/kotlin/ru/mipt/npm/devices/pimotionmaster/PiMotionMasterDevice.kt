@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package ru.mipt.npm.devices.pimotionmaster

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mipt.npm.controls.api.DeviceHub
import ru.mipt.npm.controls.api.PropertyDescriptor
import ru.mipt.npm.controls.base.*
import ru.mipt.npm.controls.controllers.DeviceFactory
import ru.mipt.npm.controls.controllers.duration
import ru.mipt.npm.controls.ports.*
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.values.asValue
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.time.Duration

class PiMotionMasterDevice(
    context: Context,
    override val deviceName: String = "PiMotionMaster",
    private val portFactory: PortFactory = KtorTcpPort,
) : DeviceBase(context), DeviceHub {

    override val scope: CoroutineScope = CoroutineScope(
        context.coroutineContext + SupervisorJob(context.coroutineContext[Job])
    )

    private var port: Port? = null
    //TODO make proxy work
    //PortProxy { portFactory(address ?: error("The device is not connected"), context) }


    val connected by readingBoolean(false, descriptorBuilder = {
        info = "True if the connection address is defined and the device is initialized"
    }) {
        port != null
    }


    val connect: DeviceAction by acting({
        info = "Connect to specific port and initialize axis"
    }) { portSpec ->
        //Clear current actions if present
        if (port != null) {
            disconnect()
        }
        //Update port
        //address = portSpec.node
        port = portFactory(portSpec.node!!, context)
        connected.updateLogical(true)
//        connector.open()
        //Initialize axes
        if (portSpec != null) {
            val idn = identity.read()
            failIfError { "Can't connect to $portSpec. Error code: $it" }
            logger.info { "Connected to $idn on $portSpec" }
            val ids = request("SAI?").map { it.trim() }
            if (ids != axes.keys.toList()) {
                //re-define axes if needed
                axes = ids.associateWith { Axis(it) }
            }
            ids.map { it.asValue() }.asValue().asMetaItem()
            initialize()
            failIfError()
        }
    }

    val disconnect: DeviceAction by acting({
        info = "Disconnect the program from the device if it is connected"
    }) {
        if (port != null) {
            stop()
            port?.close()
        }
        port = null
        connected.updateLogical(false)
    }

    fun disconnect() {
        runBlocking {
            disconnect.invoke()
        }
    }

    val timeout: DeviceProperty by writingVirtual(200.asValue()) {
        info = "Timeout"
    }

    var timeoutValue: Duration by timeout.duration()

    /**
     * Name-friendly accessor for axis
     */
    var axes: Map<String, Axis> = emptyMap()
        private set

    override val devices: Map<NameToken, Axis> = axes.mapKeys { (key, _) -> NameToken(key) }

    private suspend fun failIfError(message: (Int) -> String = { "Failed with error code $it" }) {
        val errorCode = getErrorCode()
        if (errorCode != 0) error(message(errorCode))
    }

    fun connect(host: String, port: Int) {
        runBlocking {
            connect(Meta {
                "host" put host
                "port" put port
            })
        }
    }

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
        port?.send(stringToSend) ?: error("Not connected to device")
    }

    suspend fun getErrorCode(): Int = mutex.withLock {
        withTimeout(timeoutValue) {
            sendCommandInternal("ERR?")
            val errorString = port?.receiving()?.withDelimiter("\n")?.first() ?: error("Not connected to device")
            errorString.trim().toInt()
        }
    }

    /**
     * Send a synchronous request and receive a list of lines as a response
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun request(command: String, vararg arguments: String): List<String> = mutex.withLock {
        try {
            withTimeout(timeoutValue) {
                sendCommandInternal(command, *arguments)
                val phrases = port?.receiving()?.withDelimiter("\n") ?: error("Not connected to device")
                val list = phrases.transformWhile { line ->
                    emit(line)
                    line.endsWith(" \n")
                }.toList()
                list
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
            put(key, value.trim())
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

    val initialize: DeviceAction by acting {
        send("INI")
    }

    val identity: ReadOnlyDeviceProperty by readingString {
        request("*IDN?").first()
    }

    val firmwareVersion: ReadOnlyDeviceProperty by readingString {
        request("VER?").first()
    }

    val stop: DeviceAction by acting(
        descriptorBuilder = {
            info = "Stop all axis"
        },
        action = { send("STP") }
    )

    inner class Axis(val axisId: String) : DeviceBase(context) {
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
            failIfError()
            return value
        }

        private fun axisBooleanProperty(command: String, descriptorBuilder: PropertyDescriptor.() -> Unit = {}) =
            writingBoolean(
                getter = { readAxisBoolean("$command?") },
                setter = { _, newValue ->
                    writeAxisBoolean(command, newValue)
                },
                descriptorBuilder = descriptorBuilder
            )

        private fun axisNumberProperty(command: String, descriptorBuilder: PropertyDescriptor.() -> Unit = {}) =
            writingDouble(
                getter = {
                    requestAndParse("$command?", axisId)[axisId]?.toDoubleOrNull()
                        ?: error("Malformed $command response. Should include float value for $axisId")
                },
                setter = { _, newValue ->
                    send(command, axisId, newValue.toString())
                    failIfError()
                    newValue
                },
                descriptorBuilder = descriptorBuilder
            )

        val enabled by axisBooleanProperty("EAX") {
            info = "Motor enable state."
        }

        val halt: DeviceAction by acting {
            send("HLT", axisId)
        }

        val targetPosition by axisNumberProperty("MOV") {
            info = """
                Sets a new absolute target position for the specified axis.
                Servo mode must be switched on for the commanded axis prior to using this command (closed-loop operation).
            """.trimIndent()
        }

        val onTarget: TypedReadOnlyDeviceProperty<Boolean> by readingBoolean(
            descriptorBuilder = {
                info = "Queries the on-target state of the specified axis."
            },
            getter = {
                readAxisBoolean("ONT?")
            }
        )

        val reference: ReadOnlyDeviceProperty by readingBoolean(
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

        val minPosition by readingDouble(
            descriptorBuilder = {
                info = "Minimal position value for the axis"
            },
            getter = {
                requestAndParse("TMN?", axisId)[axisId]?.toDoubleOrNull()
                    ?: error("Malformed `TMN?` response. Should include float value for $axisId")
            }
        )

        val maxPosition by readingDouble(
            descriptorBuilder = {
                info = "Maximal position value for the axis"
            },
            getter = {
                requestAndParse("TMX?", axisId)[axisId]?.toDoubleOrNull()
                    ?: error("Malformed `TMX?` response. Should include float value for $axisId")
            }
        )

        val position by readingDouble(
            descriptorBuilder = {
                info = "The current axis position."
            },
            getter = {
                requestAndParse("POS?", axisId)[axisId]?.toDoubleOrNull()
                    ?: error("Malformed `POS?` response. Should include float value for $axisId")
            }
        )

        val openLoopTarget: DeviceProperty by axisNumberProperty("OMA") {
            info = "Position for open-loop operation."
        }

        val closedLoop: TypedDeviceProperty<Boolean> by axisBooleanProperty("SVO") {
            info = "Servo closed loop mode"
        }

        val velocity: TypedDeviceProperty<Double> by axisNumberProperty("VEL") {
            info = "Velocity value for closed-loop operation"
        }

        val move by acting {
            val target = it.double ?: it.node["target"].double ?: error("Unacceptable target value $it")
            closedLoop.write(true)
            //optionally set velocity
            it.node["velocity"].double?.let { v ->
                velocity.write(v)
            }
            targetPosition.write(target)
            //read `onTarget` and `position` properties in a cycle until movement is complete
            while (!onTarget.readTyped(true)) {
                position.read(true)
                delay(200)
            }
        }

        suspend fun move(target: Double) {
            move(target.asMetaItem())
        }
    }

    companion object : DeviceFactory<PiMotionMasterDevice> {
        override fun invoke(meta: Meta, context: Context): PiMotionMasterDevice = PiMotionMasterDevice(context)
    }

}