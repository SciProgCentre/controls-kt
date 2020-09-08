package ru.mipt.npm.devices.pimotionmaster

import hep.dataforge.control.api.DeviceHub
import hep.dataforge.control.base.*
import hep.dataforge.control.controllers.duration
import hep.dataforge.control.ports.Port
import hep.dataforge.control.ports.PortProxy
import hep.dataforge.control.ports.send
import hep.dataforge.control.ports.withDelimiter
import hep.dataforge.meta.MetaItem
import hep.dataforge.names.NameToken
import hep.dataforge.values.Null
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration


public class PiMotionMasterDevice(
    parentScope: CoroutineScope,
    axes: List<String>,
    private val portFactory: suspend (MetaItem<*>?) -> Port,
) : DeviceBase(), DeviceHub {

    override val scope: CoroutineScope = CoroutineScope(
        parentScope.coroutineContext + Job(parentScope.coroutineContext[Job])
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

    private suspend fun sendCommandInternal(command: String, vararg arguments: String) {
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
        withTimeout(timeoutValue) {
            sendCommandInternal(command, *arguments)
            val phrases = connector.receiving().withDelimiter("\n")
            phrases.takeWhile { it.endsWith(" \n") }.toList() + phrases.first()
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

    public val initialize: Action by acting {
        send("INI")
    }

    public val firmwareVersion: ReadOnlyDeviceProperty by readingString {
        request("VER?").first()
    }

    public inner class Axis(public val axisId: String) : DeviceBase() {
        override val scope: CoroutineScope get() = this@PiMotionMasterDevice.scope
        public val enabled: DeviceProperty by writingBoolean<Axis>(
            getter = {
                val eax = requestAndParse("EAX?", axisId)[axisId]?.toIntOrNull()
                    ?: error("Malformed EAX response. Should include integer value for $axisId")
                eax != 0
            },
            setter = { _, newValue ->
                val value = if (newValue) {
                    "1"
                } else {
                    "0"
                }
                send("EAX", axisId, value)
                newValue
            }
        )

        public val halt: Action by acting {
            send("HLT", axisId)
        }

        public val targetPosition: DeviceProperty by writingDouble<Axis>(
            getter = {
                requestAndParse("MOV?", axisId)[axisId]?.toDoubleOrNull()
                    ?: error("Malformed MOV response. Should include float value for $axisId")
            },
            setter = { _, newValue ->
                send("MOV", axisId, newValue.toString())
                newValue
            }
        )
    }

    override val devices: Map<NameToken, Axis> = axes.associate { NameToken(it) to Axis(it) }

}