package space.kscience.controls.dataplatform

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.util.SerialParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.TimeZone
import org.apache.plc4x.java.DefaultPlcDriverManager
import org.apache.plc4x.java.api.PlcConnection
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import space.kscience.dataforge.names.Name
import kotlin.time.Clock

public class DataPlatform(
    private val scope: CoroutineScope,
    public val configuration: DataPlatformConfiguration,
    public val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    public val clock: Clock = Clock.System,
) : AutoCloseable {

    private val opcClients = mutableMapOf<Name, OpcUaClient>()

    //FIXME process connection errors

    public suspend fun resolveOpcClient(name: Name): OpcUaClient = opcClients.getOrPut(name) {
        val config = configuration.sources[name] as? OpcUaConfig ?: error("No OPC source found for $name")
        OpcUaClient.create(config.host).apply {
            connect()
        }
    }

    private val plcClients = mutableMapOf<Name, PlcConnection>()

    public suspend fun resolvePlcClient(name: Name): PlcConnection = plcClients.getOrPut(name) {
        val config = configuration.sources[name] as? PlcConfig ?: error("No PLC source found for $name")
        DefaultPlcDriverManager().getConnection(config.address).apply {
            connect()
        }
    }


    private val modbusClients = mutableMapOf<Name, AbstractModbusMaster>()
    public suspend fun resolveModbusClient(name: Name): AbstractModbusMaster = modbusClients.getOrPut(name) {
        val config = configuration.sources[name] as? ModbusConfig ?: error("No Modbus source found for $name")
        when (config) {
            is ModbusRtuConfig -> {
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

            is ModbusTcpConfig -> ModbusTCPMaster(config.addr, config.port, config.timeout, true)
        }.apply {
            connect()
        }
    }


    override fun close() {
        opcClients.values.forEach { it.disconnect() }
        plcClients.values.forEach { it.close() }
        modbusClients.values.forEach { it.disconnect() }
    }
}