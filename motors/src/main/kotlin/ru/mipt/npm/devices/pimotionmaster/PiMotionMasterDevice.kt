package ru.mipt.npm.devices.pimotionmaster

import hep.dataforge.control.base.*
import hep.dataforge.control.ports.Port
import hep.dataforge.control.ports.PortProxy
import hep.dataforge.control.ports.send
import hep.dataforge.control.ports.withDelimiter
import hep.dataforge.meta.MetaItem
import hep.dataforge.values.Null
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class PiMotionMasterDevice(
    parentScope: CoroutineScope,
    private val portFactory: suspend (MetaItem<*>?) -> Port,
) : DeviceBase() {

    override val scope: CoroutineScope = CoroutineScope(
        parentScope.coroutineContext + Job(parentScope.coroutineContext[Job])
    )

    public val port: DeviceProperty by writingVirtual(Null) {
        info = "The port for TCP connector"
    }

    private val connector = PortProxy { portFactory(port.value) }

    private val mutex = Mutex()

    private suspend fun sendCommand(command: String, vararg arguments: String) {
        val joinedArguments = if (arguments.isEmpty()) {
            ""
        } else {
            arguments.joinToString(prefix = " ", separator = " ", postfix = "")
        }
        val stringToSend = "$command$joinedArguments\n"
        connector.send(stringToSend)
    }

    /**
     * Send a synchronous request and receive a list of lines as a response
     */
    private suspend fun request(command: String, vararg arguments: String): List<String> = mutex.withLock {
        sendCommand(command, *arguments)
        val phrases = connector.receiving().withDelimiter("\n")
        return@withLock phrases.takeWhile { it.endsWith(" \n") }.toList() + phrases.first()
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
            sendCommand(command, *arguments)
        }
    }

    public val initialize: Action by action {
        send("INI")
    }

    public val firmwareVersion: ReadOnlyDeviceProperty by readingString {
        request("VER?").first()
    }

    public inner class Axis(public val axisId: String) : DeviceBase() {
        override val scope: CoroutineScope get() = this@PiMotionMasterDevice.scope
        public val enabled: DeviceProperty by writingBoolean<Axis>(
            getter = {
                val result = requestAndParse("EAX?", axisId)[axisId]?.toIntOrNull()
                    ?: error("Malformed response. Should include integer value for $axisId")
                result != 0
            },
            setter = { oldValue, newValue ->
                val value = if(newValue){
                    "1"
                } else {
                    "0"
                }
                send("EAX", axisId, value)
                oldValue
            }
        )

        public val halt: Action by action {
            send("HLT", axisId)
        }
    }

    init {
        //list everything here to ensure it is initialized
        initialize
        firmwareVersion

    }


}