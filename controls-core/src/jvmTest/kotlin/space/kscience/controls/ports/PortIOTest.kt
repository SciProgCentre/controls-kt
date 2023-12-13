package space.kscience.controls.ports

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import space.kscience.dataforge.context.Global
import kotlin.test.assertEquals


internal class PortIOTest {

    @Test
    fun testDelimiteredByteArrayFlow() {
        val flow = flowOf("bb?b", "ddd?", ":defgb?:ddf", "34fb?:--").map { it.encodeToByteArray() }
        val chunked = flow.withDelimiter("?:".encodeToByteArray())
        runBlocking {
            val result = chunked.toList()
            assertEquals(3, result.size)
            assertEquals("bb?bddd?:", result[0].decodeToString())
            assertEquals("defgb?:", result[1].decodeToString())
            assertEquals("ddf34fb?:", result[2].decodeToString())
        }
    }

    @Test
    fun testUdpCommunication() = runTest {
        val receiver = UdpPort.openChannel(Global, "localhost", 8811, localPort = 8812)
        val sender = UdpPort.openChannel(Global, "localhost", 8812, localPort = 8811)

        delay(30)
        repeat(10) {
            sender.send("Line number $it\n")
        }

        val res = receiver
            .receiving()
            .withStringDelimiter("\n")
            .take(10)
            .toList()

        assertEquals("Line number 3", res[3].trim())
        receiver.close()
        sender.close()
    }
}