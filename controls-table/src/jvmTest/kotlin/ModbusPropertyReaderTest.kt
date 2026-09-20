package space.kscience.controls.tagtable

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.procimg.SimpleDigitalIn
import com.ghgande.j2mod.modbus.procimg.SimpleDigitalOut
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister
import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import space.kscience.dataforge.meta.boolean
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.float
import space.kscience.dataforge.meta.short
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ModbusPropertyReaderTest {

    @Test
    fun decodesHoldingFloatReader() {
        val decoded = Json.decodeFromString(ModbusPropertyReader.serializer(), """{"type":"holding-float"}""")
        assertEquals("ModbusHoldingFloatReader", decoded::class.simpleName)
    }

    @Test
    fun decodesCoilReader() {
        val decoded = Json.decodeFromString(ModbusPropertyReader.serializer(), """{"type":"coil"}""")
        assertEquals("ModbusCoilReader", decoded::class.simpleName)
    }

    private val port = 50211
    private val unitId = 1
    private var slave = ModbusSlaveFactory.createTCPSlave(port, 5)
    private val master = ModbusTCPMaster("localhost", port)
    private lateinit var processImage: SimpleProcessImage

    @BeforeEach
    fun setUp() {
        slave = ModbusSlaveFactory.createTCPSlave(port, 5)
        processImage = SimpleProcessImage(unitId)
        slave.addProcessImage(unitId, processImage)
        slave.open()
        master.connect()
    }

    @AfterEach
    fun tearDown() {
        if (master.isConnected) {
            master.disconnect()
        }
        ModbusSlaveFactory.close(slave)
    }

    @Test
    fun readsHoldingFloatInBigEndianWordOrder() {
        val bits = java.lang.Float.floatToIntBits(1.5f)
        val high = (bits ushr 16) and 0xFFFF
        val low = bits and 0xFFFF
        processImage.addRegister(0, SimpleRegister(high))
        processImage.addRegister(1, SimpleRegister(low))

        val meta = ModbusHoldingFloatReader.read(master, unitId, 0)
        assertEquals(1.5f, meta.float)
    }

    @Test
    fun readsHoldingFloatDoesNotDecodeFromSwappedWordOrder() {
        val bits = java.lang.Float.floatToIntBits(1.5f)
        val high = (bits ushr 16) and 0xFFFF
        val low = bits and 0xFFFF
        // registers written low, high - the reverse of the expected big-endian word order
        processImage.addRegister(0, SimpleRegister(low))
        processImage.addRegister(1, SimpleRegister(high))

        val meta = ModbusHoldingFloatReader.read(master, unitId, 0)
        assertNotEquals(1.5f, meta.float)
    }

    @Test
    fun readsFloatFromInputRegistersInBigEndianWordOrder() {
        val bits = java.lang.Float.floatToIntBits(1.5f)
        val high = (bits ushr 16) and 0xFFFF
        val low = bits and 0xFFFF
        processImage.addInputRegister(0, SimpleInputRegister(high))
        processImage.addInputRegister(1, SimpleInputRegister(low))

        val meta = ModbusFloatReader.read(master, unitId, 0)
        assertEquals(1.5f, meta.float)
    }

    @Test
    fun readsFloatFromInputRegistersDoesNotDecodeFromSwappedWordOrder() {
        val bits = java.lang.Float.floatToIntBits(1.5f)
        val high = (bits ushr 16) and 0xFFFF
        val low = bits and 0xFFFF
        processImage.addInputRegister(0, SimpleInputRegister(low))
        processImage.addInputRegister(1, SimpleInputRegister(high))

        val meta = ModbusFloatReader.read(master, unitId, 0)
        assertNotEquals(1.5f, meta.float)
    }

    @Test
    fun readsHoldingDouble() {
        val bits = java.lang.Double.doubleToLongBits(2.25)
        val r0 = ((bits ushr 48) and 0xFFFF).toInt()
        val r1 = ((bits ushr 32) and 0xFFFF).toInt()
        val r2 = ((bits ushr 16) and 0xFFFF).toInt()
        val r3 = (bits and 0xFFFF).toInt()
        processImage.addRegister(0, SimpleRegister(r0))
        processImage.addRegister(1, SimpleRegister(r1))
        processImage.addRegister(2, SimpleRegister(r2))
        processImage.addRegister(3, SimpleRegister(r3))

        val meta = ModbusHoldingDoubleReader.read(master, unitId, 0)
        assertEquals(2.25, meta.double)
    }

    @Test
    fun readsHoldingShort() {
        processImage.addRegister(0, SimpleRegister(1234))

        val meta = ModbusHoldingShortReader.read(master, unitId, 0)
        assertEquals(1234.toShort(), meta.short)
    }

    @Test
    fun readsCoil() {
        processImage.addDigitalOut(0, SimpleDigitalOut(true))

        val meta = ModbusCoilReader.read(master, unitId, 0)
        assertEquals(true, meta.boolean)
    }

    @Test
    fun readsDiscrete() {
        processImage.addDigitalIn(0, SimpleDigitalIn(true))

        val meta = ModbusDiscreteReader.read(master, unitId, 0)
        assertEquals(true, meta.boolean)
    }

}
