package center.sciprog.devices.mks

import kotlinx.coroutines.withTimeoutOrNull
import space.kscience.controls.api.Device
import space.kscience.controls.ports.Ports
import space.kscience.controls.ports.SynchronousPort
import space.kscience.controls.ports.respondStringWithDelimiter
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.get
import kotlin.time.Duration.Companion.milliseconds

public object MksPdr900Device : DeviceFactory<SynchronousPort>() {

    public val address: DevicePropertySpec<Int> by property(MetaConverter.int, name = "address") { 253 }

    private suspend fun SynchronousPort.talk(address: Int, requestContent: String): String {
        val responsePattern = ("@${address}ACK(.*);FF").toRegex()
        return withTimeoutOrNull(5000.milliseconds) {
            val answer = respondStringWithDelimiter(String.format("@%s%s;FF", address, requestContent), ";FF")
            responsePattern.matchEntire(answer)?.groups?.get(1)?.value
                ?: error("Message $answer does not match $responsePattern")
        } ?: error("Timeout waiting for response to $requestContent")
    }

    public val powerOn by mutableBooleanProperty(
        read = {
            val addressValue = (this as Device).getOrRead(address)
            when (val answer = talk(addressValue, "FP?")) {
                "ON" -> true
                "OFF" -> false
                else -> error("Unknown answer for 'FP?': $answer")
            }
        },
        write = { value ->
            val device = this as Device
            val addressValue = device.getOrRead(address)
            val expected = if (value) "ON" else "OFF"
            val ans = talk(addressValue, "FP!$expected")
            if (ans != expected) {
                device.writeProperty("error", MetaConverter.string.convert("Failed to set power state"))
            }
        }
    )

    public val channel by property(MetaConverter.int) { 5 }

    public val value by doubleProperty {
        val device = this as Device
        val addressValue = device.getOrRead(address)
        val ch = device.getOrRead(channel)
        val answer = talk(addressValue, "PR$ch?")
        if (answer.isEmpty()) {
            device.writeProperty("error", MetaConverter.string.convert("No connection"))
            error("No connection")
        } else {
            val res = answer.toDouble()
            if (res <= 0) {
                device.writeProperty("powerOn", MetaConverter.boolean.convert(false))
                device.writeProperty("error", MetaConverter.string.convert("No power"))
                error("No power")
            } else {
                res
            }
        }
    }

    public val error by logicalProperty(MetaConverter.string, "")

    override suspend fun DeviceBase.createState(): SynchronousPort {
        val ports = context.request(Ports)
        return ports.buildSynchronousPort(meta["port"] ?: error("Port is not defined in device configuration"))
    }

    override suspend fun DeviceBase.destroyState(state: SynchronousPort) {
        state.stop()
    }
}
