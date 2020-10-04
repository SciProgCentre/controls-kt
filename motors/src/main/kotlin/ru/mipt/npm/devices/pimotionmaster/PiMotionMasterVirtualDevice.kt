package ru.mipt.npm.devices.pimotionmaster

import hep.dataforge.context.Context
import hep.dataforge.control.ports.AbstractPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration

public abstract class VirtualDevice {
    protected abstract val scope: CoroutineScope

    public abstract suspend fun evaluateRequest(request: ByteArray)

    private val toSend = Channel<ByteArray>(100)

    public val responses: Flow<ByteArray> get() = toSend.receiveAsFlow()

    protected suspend fun send(response: ByteArray) {
        toSend.send(response)
    }
//
//    protected suspend fun respond(response: String){
//        respond(response.encodeToByteArray())
//    }

    protected fun respondInFuture(delay: Duration, response: suspend () -> ByteArray): Job = scope.launch {
        delay(delay)
        send(response())
    }
}

public class VirtualPort(private val device: VirtualDevice, context: Context) : AbstractPort(context) {

    private val respondJob = scope.launch {
        device.responses.collect {
            receive(it)
        }
    }

    override suspend fun write(data: ByteArray) {
        device.evaluateRequest(data)
    }

    override fun close() {
        respondJob.cancel()
        super.close()
    }
}


class PiMotionMasterVirtualDevice(override val scope: CoroutineScope, axisIds: List<String>) : VirtualDevice() {

    init {
        //add asynchronous send logic here
    }

    private val axisID = "0"

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
        send((str + "\n").encodeToByteArray())
    }

    private fun respondForAllAxis(axisIds: List<String>, extract: VirtualAxisState.(index: String) -> Any) {
        val selectedAxis = if (axisIds.isEmpty()) {
            axisState.keys
        } else {
            axisIds
        }
        val response = selectedAxis.joinToString(postfix = "\n", separator = " \n") {
            axisState.getValue(it).extract(it).toString()
        }
        respond(response)
    }

    override suspend fun evaluateRequest(request: ByteArray) {
        assert(request.last() == '\n'.toByte())
        val string = request.decodeToString().substringBefore("\n")
        val parts = string.split(' ')
        val command = parts.firstOrNull() ?: error("Command not present")

        val axisIds: List<String> = parts.drop(1)

        when (command) {
            "XXX" -> respond("")
            "IDN?" -> respond("DataForge-device demo")
            "VER?" -> respond("test")
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
            "ERR?" -> respond(errorCode.toString())
            "SAI?" -> respondForAllAxis(axisIds) { it }
            "CST?" -> respond(WAT)
            "RON?" -> respondForAllAxis(axisIds) { referenceMode }
            "FRF?" -> respondForAllAxis(axisIds) { "1" } // WAT?
            "SVO?" -> respondForAllAxis(axisIds) { servoMode }
            "MVO?" -> respondForAllAxis(axisIds) { targetPosition }
            "POS?" -> respondForAllAxis(axisIds) { position }
            "TMN?" -> respondForAllAxis(axisIds) { minPosition }
            "TMX?" -> respondForAllAxis(axisIds) { maxPosition }
            "VEL?" -> respondForAllAxis(axisIds) { velocity }
            "SRG?" -> respond(WAT)
            "SVO" -> {
                val requestAxis = parts[1]
                val servoMode = parts.last()
                axisState[requestAxis]?.servoMode = servoMode.toInt()
            }
            else -> errorCode = 2 // do not send anything. Ser error code
        }
    }

    companion object {
        private const val WAT = "WAT?"
    }
}
