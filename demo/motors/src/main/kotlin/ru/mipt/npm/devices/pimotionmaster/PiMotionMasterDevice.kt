@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package ru.mipt.npm.devices.pimotionmaster

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import space.kscience.controls.api.DeviceHub
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.ports.*
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.transformations.MetaConverter
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.values.asValue
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class PiMotionMasterDevice(
    context: Context,
    private val portFactory: PortFactory = KtorTcpPort,
) : DeviceBySpec<PiMotionMasterDevice>(PiMotionMasterDevice, context), DeviceHub {

    private var port: Port? = null
    //TODO make proxy work
    //PortProxy { portFactory(address ?: error("The device is not connected"), context) }


    fun disconnect() {
        runBlocking {
            disconnect.invoke()
        }
    }

    var timeoutValue: Duration = 200.milliseconds

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
                phrases.transformWhile { line ->
                    emit(line)
                    line.endsWith(" \n")
                }.toList()
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

    companion object : DeviceSpec<PiMotionMasterDevice>(), Factory<PiMotionMasterDevice> {

        override fun invoke(meta: Meta, context: Context): PiMotionMasterDevice = PiMotionMasterDevice(context)

        val connected by booleanProperty(descriptorBuilder = {
            info = "True if the connection address is defined and the device is initialized"
        }) {
            port != null
        }


        val initialize by unitAction {
            send("INI")
        }

        val identity by stringProperty {
            request("*IDN?").first()
        }

        val firmwareVersion by stringProperty {
            request("VER?").first()
        }

        val stop by unitAction({
            info = "Stop all axis"
        }) {
            send("STP")
        }

        val connect by metaAction(descriptorBuilder = {
            info = "Connect to specific port and initialize axis"
        }) { portSpec ->
            //Clear current actions if present
            if (port != null) {
                disconnect()
            }
            //Update port
            //address = portSpec.node
            port = portFactory(portSpec ?: Meta.EMPTY, context)
            updateLogical(connected, true)
//        connector.open()
            //Initialize axes
            if (portSpec != null) {
                val idn = identity.read()
                failIfError { "Can't connect to $portSpec. Error code: $it" }
                logger.info { "Connected to $idn on $portSpec" }
                val ids = request("SAI?").map { it.trim() }
                if (ids != axes.keys.toList()) {
                    //re-define axes if needed
                    axes = ids.associateWith { Axis(this, it) }
                }
                Meta(ids.map { it.asValue() }.asValue())
                initialize()
                failIfError()
            }
            null
        }

        val disconnect by metaAction({
            info = "Disconnect the program from the device if it is connected"
        }) {
            if (port != null) {
                stop()
                port?.close()
            }
            port = null
            updateLogical(connected, false)
            null
        }


        val timeout by mutableProperty(MetaConverter.duration, PiMotionMasterDevice::timeoutValue) {
            info = "Timeout"
        }
    }


    class Axis(
        val mm: PiMotionMasterDevice,
        val axisId: String
    ) : DeviceBySpec<Axis>(Axis, mm.context) {

        /**
         * TODO Move to head device and abstract
         */
        private suspend fun readAxisBoolean(command: String): Boolean =
            (mm.requestAndParse(command, axisId)[axisId]?.toIntOrNull()
                ?: error("Malformed $command response. Should include integer value for $axisId")) != 0

        /**
         * TODO Move to head device and abstract
         */
        private suspend fun writeAxisBoolean(command: String, value: Boolean): Boolean {
            val boolean = if (value) {
                "1"
            } else {
                "0"
            }
            mm.send(command, axisId, boolean)
            mm.failIfError()
            return value
        }

        suspend fun move(target: Double) {
            move(target.asMeta())
        }

        companion object : DeviceSpec<Axis>() {

            private fun axisBooleanProperty(
                command: String,
                descriptorBuilder: PropertyDescriptor.() -> Unit = {}
            ) = booleanProperty(
                read = {
                    readAxisBoolean("$command?")
                },
                write = {
                    writeAxisBoolean(command, it)
                },
                descriptorBuilder = descriptorBuilder
            )

            private fun axisNumberProperty(
                command: String,
                descriptorBuilder: PropertyDescriptor.() -> Unit = {}
            ) = doubleProperty(
                read = {
                    mm.requestAndParse("$command?", axisId)[axisId]?.toDoubleOrNull()
                        ?: error("Malformed $command response. Should include float value for $axisId")
                },
                write = { newValue ->
                    mm.send(command, axisId, newValue.toString())
                    mm.failIfError()
                },
                descriptorBuilder = descriptorBuilder
            )

            val enabled by axisBooleanProperty("EAX") {
                info = "Motor enable state."
            }

            val halt by unitAction {
                mm.send("HLT", axisId)
            }

            val targetPosition by axisNumberProperty("MOV") {
                info = """
                Sets a new absolute target position for the specified axis.
                Servo mode must be switched on for the commanded axis prior to using this command (closed-loop operation).
            """.trimIndent()
            }

            val onTarget by booleanProperty({
                info = "Queries the on-target state of the specified axis."
            }) {
                readAxisBoolean("ONT?")
            }

            val reference by booleanProperty({
                info = "Get Referencing Result"
            }) {
                readAxisBoolean("FRF?")
            }

            val moveToReference by unitAction {
                mm.send("FRF", axisId)
            }

            val minPosition by doubleProperty({
                info = "Minimal position value for the axis"
            }) {
                mm.requestAndParse("TMN?", axisId)[axisId]?.toDoubleOrNull()
                    ?: error("Malformed `TMN?` response. Should include float value for $axisId")
            }

            val maxPosition by doubleProperty({
                info = "Maximal position value for the axis"
            }) {
                mm.requestAndParse("TMX?", axisId)[axisId]?.toDoubleOrNull()
                    ?: error("Malformed `TMX?` response. Should include float value for $axisId")
            }

            val position by doubleProperty({
                info = "The current axis position."
            }) {
                mm.requestAndParse("POS?", axisId)[axisId]?.toDoubleOrNull()
                    ?: error("Malformed `POS?` response. Should include float value for $axisId")
            }

            val openLoopTarget by axisNumberProperty("OMA") {
                info = "Position for open-loop operation."
            }

            val closedLoop by axisBooleanProperty("SVO") {
                info = "Servo closed loop mode"
            }

            val velocity by axisNumberProperty("VEL") {
                info = "Velocity value for closed-loop operation"
            }

            val move by metaAction {
                val target = it.double ?: it?.get("target").double ?: error("Unacceptable target value $it")
                closedLoop.write(true)
                //optionally set velocity
                it?.get("velocity").double?.let { v ->
                    velocity.write(v)
                }
                targetPosition.write(target)
                //read `onTarget` and `position` properties in a cycle until movement is complete
                while (!onTarget.read()) {
                    position.read()
                    delay(200)
                }
                null
            }

        }

    }

}