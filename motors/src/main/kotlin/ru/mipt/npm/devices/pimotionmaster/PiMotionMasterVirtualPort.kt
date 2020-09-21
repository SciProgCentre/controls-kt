package ru.mipt.npm.devices.pimotionmaster

import hep.dataforge.context.Context
import hep.dataforge.control.ports.AbstractPort

abstract class VirtualPort(context: Context) : AbstractPort(context)


class PiMotionMasterVirtualPort(context: Context) : VirtualPort(context) {

    init {
        //add asynchronous send logic here
    }

    private val axisID = "0"

    private var errorCode: Int = 0
    private var velocity: Float = 1.0f
    private var position: Float = 0.0f
    private var servoMode: Int = 1
    private var targetPosition: Float = 0.0f


    private fun receive(str: String) = receive((str + "\n").toByteArray())

    override suspend fun write(data: ByteArray) {
        assert(data.last() == '\n'.toByte())
        val string = data.decodeToString().substringBefore("\n")
        val parts = string.split(' ')
        val command = parts.firstOrNull() ?: error("Command not present")
        when (command) {
            "XXX" -> receive("WAT?")
            "VER?" -> receive("test")
            "ERR?" -> receive(errorCode.toString())
            "SVO?" -> receive("$axisID=$servoMode")
            "SVO" ->{
                val requestAxis = parts[1]
                if(requestAxis == axisID) {
                    servoMode = parts[2].toInt()
                }
            }
            else -> errorCode = 2 // do not send anything. Ser error code
        }
    }
}