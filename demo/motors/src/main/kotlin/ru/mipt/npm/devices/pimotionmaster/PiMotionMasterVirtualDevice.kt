package ru.mipt.npm.devices.pimotionmaster

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.Socket
import space.kscience.controls.ports.AbstractPort
import space.kscience.controls.ports.withDelimiter
import space.kscience.dataforge.context.*
import kotlin.math.abs
import kotlin.time.Duration

abstract class VirtualDevice(val scope: CoroutineScope) : Socket<ByteArray> {

    protected abstract suspend fun evaluateRequest(request: ByteArray)

    protected open fun Flow<ByteArray>.transformRequests(): Flow<ByteArray> = this

    private val toReceive = Channel<ByteArray>(100)
    private val toRespond = Channel<ByteArray>(100)

    private val mutex = Mutex()

    private val receiveJob: Job = toReceive.consumeAsFlow().transformRequests().onEach {
        mutex.withLock {
            evaluateRequest(it)
        }
    }.catch {
        it.printStackTrace()
    }.launchIn(scope)


    override suspend fun send(data: ByteArray) {
        toReceive.send(data)
    }

    protected suspend fun respond(response: ByteArray) {
        toRespond.send(response)
    }

    override fun receiving(): Flow<ByteArray> = toRespond.receiveAsFlow()

    protected fun respondInFuture(delay: Duration, response: suspend () -> ByteArray): Job = scope.launch {
        delay(delay)
        respond(response())
    }

    override fun isOpen(): Boolean = scope.isActive

    override fun close() = scope.cancel()
}

class VirtualPort(private val device: VirtualDevice, context: Context) : AbstractPort(context) {

    private val respondJob = device.receiving().onEach {
        receive(it)
    }.catch {
        it.printStackTrace()
    }.launchIn(scope)


    override suspend fun write(data: ByteArray) {
        device.send(data)
    }

    override fun close() {
        respondJob.cancel()
        super.close()
    }
}


class PiMotionMasterVirtualDevice(
    override val context: Context,
    axisIds: List<String>,
    scope: CoroutineScope = context,
) : VirtualDevice(scope), ContextAware {

    init {
        //add asynchronous send logic here
    }

    override fun Flow<ByteArray>.transformRequests(): Flow<ByteArray> = withDelimiter("\n".toByteArray())

    private var errorCode: Int = 0

    private val axisState: Map<String, VirtualAxisState> = axisIds.associateWith { VirtualAxisState() }

    private inner class VirtualAxisState {
        private var movementJob: Job? = null

        private fun startMovement() {
            movementJob?.cancel()
            movementJob = scope.launch {
                while (!onTarget()) {
                    delay(100)
                    val proposedStep = velocity / 10
                    val distance = targetPosition - position
                    when {
                        abs(distance) < proposedStep -> {
                            position = targetPosition
                        }
                        targetPosition > position -> {
                            position += proposedStep
                        }
                        else -> {
                            position -= proposedStep
                        }
                    }
                }
            }
        }

        var referenceMode = 1

        var velocity = 0.6

        var position = 0.0
            private set
        var servoMode: Int = 1

        var targetPosition = 0.0
            set(value) {
                field = value
                if (servoMode == 1) {
                    startMovement()
                }
            }

        fun onTarget() = abs(targetPosition - position) < 0.001

        val minPosition = 0.0
        val maxPosition = 26.0
    }


    private fun respond(str: String) = scope.launch {
        respond((str + "\n").encodeToByteArray())
    }

    private fun respondForAllAxis(axisIds: List<String>, extract: VirtualAxisState.(index: String) -> Any) {
        val selectedAxis = if (axisIds.isEmpty() || axisIds[0] == "ALL") {
            axisState.keys
        } else {
            axisIds
        }
        val response = selectedAxis.joinToString(separator = " \n") {
            val state = axisState.getValue(it)
            val value = when (val extracted = state.extract(it)) {
                true -> 1
                false -> 0
                else -> extracted
            }
            "$it=$value"
        }
        respond(response)
    }

    private suspend fun doForEachAxis(parts: List<String>, action: suspend (key: String, value: String) -> Unit) {
        var i = 0
        while (parts.size > 2 * i + 1) {
            action(parts[2 * i + 1], parts[2 * i + 2])
            i++
        }
    }

    override suspend fun evaluateRequest(request: ByteArray) {
        assert(request.last() == '\n'.code.toByte())
        val string = request.decodeToString().substringBefore("\n")
            .dropWhile { it != '*' && it != '#' && it !in 'A'..'Z' } //filter junk symbols at the beginning of the line

        //logger.debug { "Received command: $string" }
        val parts = string.split(' ')
        val command = parts.firstOrNull() ?: error("Command not present")

        val axisIds: List<String> = parts.drop(1)

        when (command) {
            "XXX" -> {
            }
            "IDN?", "*IDN?" -> respond("(c)2015 Physik Instrumente(PI) Karlsruhe, C-885.M1 TCP-IP Master,0,1.0.0.1")
            "VER?" -> respond("""
                2: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550039, 00.039 
                3: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550040, 00.039 
                4: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550041, 00.039 
                5: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550042, 00.039 
                6: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550043, 00.039 
                7: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550044, 00.039 
                8: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550046, 00.039 
                9: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550045, 00.039 
                10: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550047, 00.039 
                11: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550048, 00.039 
                12: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550049, 00.039 
                13: (c)2017 Physik Instrumente (PI) GmbH & Co. KG, C-663.12C885, 018550051, 00.039 
                FW_ARM: V1.0.0.1
            """.trimIndent())
            "HLP?" -> respond("""
                The following commands are valid: 
                #4 Request Status Register 
                #5 Request Motion Status 
                #7 Request Controller Ready Status 
                #24 Stop All Axes 
                *IDN? Get Device Identification 
                CST? [{<AxisID>}] Get Assignment Of Stages To Axes 
                CSV? Get Current Syntax Version 
                ERR? Get Error Number 
                FRF [{<AxisID>}] Fast Reference Move To Reference Switch 
                FRF? [{<AxisID>}] Get Referencing Result 
                HLP? Get List Of Available Commands 
                HLT [{<AxisID>}] Halt Motion Smoothly 
                IFC {<InterfacePam> <PamValue>} Set Interface Parameters Temporarily 
                IFC? [{<InterfacePam>}] Get Current Interface Parameters 
                IFS <Pswd> {<InterfacePam> <PamValue>} Set Interface Parameters As Default Values 
                IFS? [{<InterfacePam>}] Get Interface Parameters As Default Values 
                INI Initialize Axes 
                MAN? <CMD> Get Help String For Command 
                MOV {<AxisID> <Position>} Set Target Position (start absolute motion) 
                MOV? [{<AxisID>}] Get Target Position 
                ONT? [{<AxisID>}] Get On-Target State 
                POS {<AxisID> <Position>} Set Real Position (does not cause motion)
                POS? [{<AxisID>}] Get Real Position 
                RBT Reboot System 
                RON {<AxisID> <ReferenceOn>} Set Reference Mode 
                RON? [{<AxisID>}] Get Reference Mode 
                SAI? [ALL] Get List Of Current Axis Identifiers 
                SRG? {<AxisID> <RegisterID>} Query Status Register Value 
                STP Stop All Axes 
                SVO {<AxisID> <ServoState>} Set Servo Mode 
                SVO? [{<AxisID>}] Get Servo Mode 
                TMN? [{<AxisID>}] Get Minimum Commandable Position 
                TMX? [{<AxisID>}] Get Maximum Commandable Position 
                VEL {<AxisID> <Velocity>} Set Closed-Loop Velocity 
                VEL? [{<AxisID>}] Get Closed-Loop Velocity 
                VER? Get Versions Of Firmware And Drivers 
                end of help
            """.trimIndent())
            "ERR?" -> {
                respond(errorCode.toString())
                errorCode = 0
            }
            "SAI?" -> respond(axisState.keys.joinToString(separator = " \n"))
            "CST?" -> respondForAllAxis(axisIds) { "L-220.20SG" }
            "RON?" -> respondForAllAxis(axisIds) { referenceMode }
            "FRF?" -> respondForAllAxis(axisIds) { "1" } // WAT?
            "SVO?" -> respondForAllAxis(axisIds) { servoMode }
            "MOV?" -> respondForAllAxis(axisIds) { targetPosition }
            "POS?" -> respondForAllAxis(axisIds) { position }
            "TMN?" -> respondForAllAxis(axisIds) { minPosition }
            "TMX?" -> respondForAllAxis(axisIds) { maxPosition }
            "VEL?" -> respondForAllAxis(axisIds) { velocity }
            "SRG?" -> respond(WAT)
            "ONT?" -> respondForAllAxis(axisIds) { onTarget() }
            "SVO" -> doForEachAxis(parts) { key, value ->
                axisState[key]?.servoMode = value.toInt()
            }
            "MOV" -> doForEachAxis(parts) { key, value ->
                axisState[key]?.targetPosition = value.toDouble()
            }
            "VEL" -> doForEachAxis(parts) { key, value ->
                axisState[key]?.velocity = value.toDouble()
            }
            "INI" -> {
                logger.info { "Axes initialized!" }
            }
            else -> {
                logger.warn { "Unknown command: $command in message ${String(request)}" }
                errorCode = 2
            } // do not send anything. Ser error code
        }
    }

    companion object {
        private const val WAT = "WAT?"
    }
}
