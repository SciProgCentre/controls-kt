package center.sciprog.devices.mks

import kotlinx.coroutines.withTimeoutOrNull
import space.kscience.controls.ports.Ports
import space.kscience.controls.ports.SynchronousPort
import space.kscience.controls.ports.respondStringWithDelimiter
import space.kscience.controls.ports.synchronous
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.transformations.MetaConverter


//TODO this device is not tested
class MksPdr900Device(context: Context, meta: Meta) : DeviceBySpec<MksPdr900Device>(MksPdr900Device, context, meta) {

    private val address by meta.int(253)

    private val portDelegate = lazy {
        val ports = context.request(Ports)
        ports.buildPort(meta["port"] ?: error("Port is not defined in device configuration")).synchronous()
    }

    private val port: SynchronousPort by portDelegate

    private val responsePattern: Regex by lazy {
        ("@${address}ACK(.*);FF").toRegex()
    }

    private suspend fun talk(requestContent: String): String? = withTimeoutOrNull(5000) {
        val answer = port.respondStringWithDelimiter(String.format("@%s%s;FF", address, requestContent), ";FF")
        responsePattern.matchEntire(answer)?.groups?.get(1)?.value
            ?: error("Message $answer does not match $responsePattern")
    }

    public suspend fun readPowerOn(): Boolean = when (val answer = talk("FP?")) {
        "ON" -> true
        "OFF" -> false
        else -> error("Unknown answer for 'FP?': $answer")
    }


    public suspend fun writePowerOn(powerOnValue: Boolean) {
        error.invalidate()
        if (powerOnValue) {
            val ans = talk("FP!ON")
            if (ans == "ON") {
                updateLogical(powerOn, true)
            } else {
                updateLogical(error, "Failed to set power state")
            }
        } else {
            val ans = talk("FP!OFF")
            if (ans == "OFF") {
                updateLogical(powerOn, false)
            } else {
                updateLogical(error, "Failed to set power state")
            }
        }
    }

    public suspend fun readChannelData(channel: Int): Double? {
        val answer: String? = talk("PR$channel?")
        error.invalidate()
        return if (answer.isNullOrEmpty()) {
            //            updateState(PortSensor.CONNECTED_STATE, false)
            updateLogical(error, "No connection")
            null
        } else {
            val res = answer.toDouble()
            if (res <= 0) {
                updateLogical(powerOn, false)
                updateLogical(error, "No power")
                null
            } else {
                res
            }
        }
    }


    companion object : DeviceSpec<MksPdr900Device>(), Factory<MksPdr900Device> {

        const val DEFAULT_CHANNEL: Int = 5

        override fun build(context: Context, meta: Meta): MksPdr900Device = MksPdr900Device(context, meta)

        val powerOn by booleanProperty(read = MksPdr900Device::readPowerOn, write = MksPdr900Device::writePowerOn)

        val channel by logicalProperty(MetaConverter.int)

        val value by doubleProperty(read = {
            readChannelData(channel.get() ?: DEFAULT_CHANNEL)
        })

        val error by logicalProperty(MetaConverter.string)


        override fun MksPdr900Device.onClose() {
            if (portDelegate.isInitialized()) {
                port.close()
            }
        }
    }
}
