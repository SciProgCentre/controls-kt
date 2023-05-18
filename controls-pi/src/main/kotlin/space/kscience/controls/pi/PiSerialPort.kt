package space.kscience.controls.pi

import com.pi4j.io.serial.FlowControl
import com.pi4j.io.serial.Parity
import com.pi4j.io.serial.Serial
import com.pi4j.io.serial.StopBits
import com.pi4j.ktx.io.open
import com.pi4j.ktx.io.piGpioSerialProvider
import com.pi4j.ktx.io.serial
import space.kscience.controls.ports.AbstractPort
import space.kscience.dataforge.context.Context
import kotlin.coroutines.CoroutineContext

public class PiSerialPort(
    context: Context,
    coroutineContext: CoroutineContext = context.coroutineContext,
    public val serialBuilder: () -> Serial,
) : AbstractPort(context, coroutineContext) {

    private val serial by lazy { serialBuilder() }

    override suspend fun write(data: ByteArray) {
        TODO()
    }
}