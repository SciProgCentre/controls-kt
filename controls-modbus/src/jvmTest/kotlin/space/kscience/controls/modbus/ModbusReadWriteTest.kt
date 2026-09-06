package space.kscience.controls.modbus

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.procimg.SimpleDigitalOut
import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readDouble
import kotlinx.io.writeDouble
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import space.kscience.dataforge.io.IOFormat
import kotlin.test.assertEquals

class ModbusReadWriteTest {

    private val port = 50201
    private val unitId = 1
    private var slave = ModbusSlaveFactory.createTCPSlave(port, 5)
    private val master = ModbusTCPMaster("localhost", port)

    private val doubleFormat = object : IOFormat<Double> {
        override fun readFrom(source: Source): Double = source.readDouble()
        override fun writeTo(sink: Sink, obj: Double) {
            sink.writeDouble(obj)
        }
    }

    @BeforeEach
    fun setUp() {
        slave = ModbusSlaveFactory.createTCPSlave(port, 5)
        val processImage = SimpleProcessImage(unitId).apply {
            addDigitalOut(0, SimpleDigitalOut(false))
            addRegister(0, SimpleRegister(0))
            addRegister(1, SimpleRegister(0))
            addRegister(2, SimpleRegister(0))
            addRegister(3, SimpleRegister(0))
            addRegister(4, SimpleRegister(0))
        }
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
    fun testWriteAndReadCoil() {
        val coilKey = ModbusRegistryKey.Coil(0)
        assertEquals(false, master.readCoil(unitId, coilKey))

        master.writeCoil(unitId, coilKey, true)
        assertEquals(true, master.readCoil(unitId, coilKey))

        master.write(unitId, coilKey, false)
        assertEquals(false, master.read(unitId, coilKey))
    }

    @Test
    fun testWriteAndReadHoldingRegister() {
        val regKey = ModbusRegistryKey.HoldingRegister(0)
        assertEquals(0.toShort(), master.readHoldingRegister(unitId, regKey))

        master.writeHoldingRegister(unitId, regKey, 1234.toShort())
        assertEquals(1234.toShort(), master.readHoldingRegister(unitId, regKey))

        master.write(unitId, regKey, 5678.toShort())
        assertEquals(5678.toShort(), master.read(unitId, regKey))
    }

    @Test
    fun testWriteAndReadHoldingRange() {
        val rangeKey = ModbusRegistryKey.HoldingRange(1, 4, doubleFormat)
        val testValue = 3.141592653589793

        master.writeHoldingRegisters(unitId, rangeKey, testValue)
        assertEquals(testValue, master.readHoldingRegisters(unitId, rangeKey))

        val newValue = 2.718281828459045
        master.write(unitId, rangeKey, newValue)
        assertEquals(newValue, master.read(unitId, rangeKey))
    }

    @Test
    fun testWriteToReadOnlyRegistersFails() {
        val discreteKey = ModbusRegistryKey.DiscreteInput(0)
        val inputRegKey = ModbusRegistryKey.InputRegister(0)
        val inputRangeKey = ModbusRegistryKey.InputRange(0, 4, doubleFormat)

        kotlin.test.assertFailsWith<IllegalStateException> {
            master.write(unitId, discreteKey, true)
        }
        kotlin.test.assertFailsWith<IllegalStateException> {
            master.write(unitId, inputRegKey, 1.toShort())
        }
        kotlin.test.assertFailsWith<IllegalStateException> {
            master.write(unitId, inputRangeKey, 1.0)
        }
    }
}
