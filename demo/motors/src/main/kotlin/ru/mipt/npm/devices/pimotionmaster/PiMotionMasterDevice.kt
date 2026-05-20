@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package ru.mipt.npm.devices.pimotionmaster

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.duration
import space.kscience.controls.ports.AsynchronousPort
import space.kscience.controls.ports.KtorTcpPort
import space.kscience.controls.ports.send
import space.kscience.controls.ports.withStringDelimiter
import space.kscience.controls.spec.*
import space.kscience.controls.unit
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.asValue
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


object PiMotionMaster : AbstractDeviceSpec(), Factory<DeviceTree> {
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

    override fun build(
        context: Context,
        meta: Meta
    ): DeviceTree {


        var connector: PiMotionMasterConnector? = null

        var axes: Map<String, DeviceTree> = emptyMap()

        val rootDevice = Device(context, meta, PiMotionMaster) {

            logical(PiMotionMaster.timeout, 200.milliseconds)

            action(PiMotionMaster.disconnect) {
                connector?.let {
                    execute(PiMotionMaster.stop)
                    it.port.stop()
                }
                connector = null
                contextOf<DeviceBase>().propertyChanged(PiMotionMaster.connected, false)
            }

            action(PiMotionMaster.connect) { portSpec ->
                //Clear current actions if present
                if (connector != null) {
                    execute(PiMotionMaster.disconnect)
                }

                //Update port
                //address = portSpec.node
                val port = KtorTcpPort(portSpec, context).apply { start() }

                connector = PiMotionMasterConnector(context, port).apply {

//        connector.open()
                    //Initialize axes
                    val idn = read(PiMotionMaster.identity)
                    failIfError { "Can't connect to $portSpec. Error code: $it" }
                    contextOf<DeviceBase>().propertyChanged(PiMotionMaster.connected, true)
                    logger.info { "Connected to $idn on $portSpec" }
                    val ids = request("SAI?").map { it.trim() }
                    if (ids != axes.keys.toList()) {
                        //re-define axes if needed
                        axes = ids.associateWith { DeviceTree(Axis.build(context, meta, this, it)) }
                    }
                    Meta(ids.map { it.asValue() }.asValue())
                    execute(PiMotionMaster.initialize)
                    failIfError()
                }
            }


            fun getConnector() = connector ?: error("Not connected to the device")

            reader(PiMotionMaster.connected) {
                connector != null
            }

            action(PiMotionMaster.initialize) {
                getConnector().send("INI")
            }

            reader(PiMotionMaster.identity) {
                getConnector().request("*IDN?").first()
            }


            reader(PiMotionMaster.firmwareVersion) {
                getConnector().request("VER?").first()
            }

            action(PiMotionMaster.stop) {
                getConnector().send("STP")
            }


        }


        return DeviceTree(rootDevice, axes)
    }
}


