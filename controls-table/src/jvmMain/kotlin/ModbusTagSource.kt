package space.kscience.controls.tagtable

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.util.SerialParameters
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaRef
import space.kscience.dataforge.meta.MetaSpec
import space.kscience.dataforge.meta.descriptors.required
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.serializable
import space.kscience.dataforge.meta.validate
import kotlin.time.Clock

public class ModbusTagSource(
    private val config: ModbusTagSourceConfig,
    private val clock: Clock
) : TagSource {

    private var client: AbstractModbusMaster? = null

    override suspend fun open() {
        client = when (config) {
            is ModbusRtuTagSourceConfig -> {
                val serialParameters = SerialParameters().apply {
                    this.portName = config.portName
                    this.baudRate = config.baudRate
                    this.databits = config.databits
                    this.stopbits = config.stopbits
                    this.parity = config.parity
                    this.flowControlIn = config.flowControlIn
                    this.flowControlOut = config.flowControlOut
                }
                ModbusSerialMaster(serialParameters, config.timeout, config.transmitDelay)
            }

            is ModbusTcpTagSourceConfig -> ModbusTCPMaster(config.addr, config.port, config.timeout, true)
        }.apply {
            connect()
        }
    }

    override fun close() {
        client?.disconnect()
        client = null
    }

    override suspend fun readTag(address: Meta): ValueWithTime<Meta>? = client?.let { client->
        val modbusAddress = address[ColumnSpec.address] ?: error("Modbus address is not defined in $address")
        val reader = address[ColumnSpec.reader] ?: error("Modbus read is not defined")
        val unitId = address[ColumnSpec.unitId] ?: 1

        val res = reader.read(client, unitId, modbusAddress)

        ValueWithTime(res, clock.now())
    }

    override fun validate(column: TagTableColumn) {
        if (!OpcTagSource.ColumnSpec.validate(column.config)) error("Invalid Modbus address for column $column")
    }

    public object ColumnSpec : MetaSpec() {
        public val address: MetaRef<Int> by int {
            required()
        }
        public val reader: MetaRef<ModbusTagReader> by serializable<ModbusTagReader>{
            required()
        }

        public val unitId: MetaRef<Int> by int()
    }

}