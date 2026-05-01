@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package ru.mipt.npm.devices.pimotionmaster

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import space.kscience.controls.duration
import space.kscience.controls.ports.AsynchronousPort
import space.kscience.controls.ports.KtorTcpPort
import space.kscience.controls.ports.send
import space.kscience.controls.ports.withStringDelimiter
import space.kscience.controls.spec.*
import space.kscience.controls.unit
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

public class PiMotionMasterConnector(
    override val context: Context,
    val port: AsynchronousPort,
    var timeoutValue: Duration = 200.milliseconds
) : ContextAware {
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
        port.send(stringToSend)
    }

    suspend fun getErrorCode(): Int = mutex.withLock {
        withTimeout(timeoutValue) {
            sendCommandInternal("ERR?")
            val errorString = port.subscribe().withStringDelimiter("\n").first()
            errorString.trim().toInt()
        }
    }


    suspend fun failIfError(message: (Int) -> String = { "Failed with error code $it" }) {
        val errorCode = getErrorCode()
        if (errorCode != 0) error(message(errorCode))
    }


    /**
     * Send a synchronous request and receive a list of lines as a response
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public suspend fun request(command: String, vararg arguments: String): List<String> = mutex.withLock {
        try {
            withTimeout(timeoutValue) {
                sendCommandInternal(command, *arguments)
                val phrases = port.subscribe().withStringDelimiter("\n")
                phrases.transformWhile { line ->
                    emit(line)
                    line.endsWith(" \n")
                }.toList()
            }
        } catch (ex: Throwable) {
            logger.error(ex) { "Error during PIMotionMaster request. Requesting error code." }
            val errorCode = getErrorCode()
            dispatchError(errorCode)
            logger.warn { "Error code $errorCode" }
            error("Error code $errorCode")
        }
    }

    public suspend fun requestAndParse(command: String, vararg arguments: String): Map<String, String> = buildMap {
        request(command, *arguments).forEach { line ->
            val (key, value) = line.split("=")
            put(key, value.trim())
        }
    }

    /**
     * Send a synchronous command
     */
    public suspend fun send(command: String, vararg arguments: String) {
        mutex.withLock {
            withTimeout(timeoutValue) {
                sendCommandInternal(command, *arguments)
            }
        }
    }
}


object PiMotionMasterSpec : AbstractDeviceSpec() {
    val connected by property(MetaConverter.boolean) {
        description = "True if the connection address is defined and the device is initialized"
    }

    val initialize by unitAction()

    val identity by property(MetaConverter.string)

    val firmwareVersion by property(MetaConverter.string)

    val stop by unitAction() {
        description = "Stop all axis"
    }

    val connect by action(MetaConverter.meta, MetaConverter.unit) {
        description = "Connect to specific port and initialize axis"
    }

    val disconnect by unitAction {
        description = "Disconnect the program from the device if it is connected"
    }


    val timeout by mutableProperty(MetaConverter.duration) {
        description = "Timeout"
    }
}

fun PiMotionMasterDevice(
    context: Context,
    meta: Meta = Meta.EMPTY,
    portFactory: Factory<AsynchronousPort> = KtorTcpPort,
)  = Device(context, meta, PiMotionMasterSpec) {

    val port: AsynchronousPort = portFactory.build(context, meta)




    /**
     * Name-friendly accessor for axis
     */
    var axes: Map<String, Axis> = emptyMap()
        private set

    override val devices: Map<Name, Axis> = axes.mapKeys { (key, _) -> key.parseAsName() }


    suspend fun connect(host: String, port: Int) {
        execute(connect, Meta {
            "host" put host
            "port" put port
        })
    }

    val connected by booleanProperty(descriptorBuilder = {
        description = "True if the connection address is defined and the device is initialized"
    }) {
        port != null
    }


    val initialize by unitAction() {
        send("INI")
    }

    val identity by stringProperty {
        request("*IDN?").first()
    }

    val firmwareVersion by stringProperty {
        request("VER?").first()
    }

    val stop by unitAction({
        send("STP")
    }) {
        description = "Stop all axis"
    }

    val connect by action(MetaConverter.meta, MetaConverter.unit, descriptorBuilder = {
        description = "Connect to specific port and initialize axis"
    }) { portSpec ->
        //Clear current actions if present
        if (port != null) {
            disconnect()
        }
        //Update port
        //address = portSpec.node
        port = portFactory(portSpec, context).apply { start() }
//        connector.open()
        //Initialize axes
        val idn = read(identity)
        failIfError { "Can't connect to $portSpec. Error code: $it" }
        propertyChanged(connected, true)
        logger.info { "Connected to $idn on $portSpec" }
        val ids = request("SAI?").map { it.trim() }
        if (ids != axes.keys.toList()) {
            //re-define axes if needed
            axes = ids.associateWith { Axis(this, it) }
        }
        Meta(ids.map { it.asValue() }.asValue())
        execute(initialize)
        failIfError()
    }

    val disconnect by unitAction({
        port?.let {
            execute(stop)
            it.stop()
        }
        port = null
        propertyChanged(connected, false)
    }) {
        description = "Disconnect the program from the device if it is connected"
    }


    val timeout by mutableProperty(MetaConverter.duration, PiMotionMasterDevice::timeoutValue) {
        description = "Timeout"
    }


}

