package space.kscience.controls.tagtable

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.util.SerialParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.isStarted
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.required
import kotlin.time.Clock

public class ModbusTagSource(
    private val config: ModbusTagSourceConfig,
    private val scope: CoroutineScope,
    private val clock: Clock
) : TagSource {

    private var client: AbstractModbusMaster? = null

    override val state: StateFlow<SourceState>
        field = MutableStateFlow(SourceState(Meta.EMPTY))


    private fun updateState(block: MutableMeta.() -> Unit) {
        state.value = state.value.copy(block)
    }

    override var lifecycleState: LifecycleState
        get() = state.value.lifeCycleState
        private set(value) {
            updateState { set(SourceState.lifeCycle, value) }
        }


    private var connectionJob: Job? = null

    override suspend fun start(): Unit {
        if (lifecycleState.isStarted()) return
        if (connectionJob?.isActive == true) return

        connectionJob = scope.launch {
            val newClient = when (config) {
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
            }

            client = newClient
            lifecycleState = LifecycleState.STARTING

            try {
                newClient.connect()
                lifecycleState = LifecycleState.STARTED
            } catch (e: Exception) {
                lifecycleState = LifecycleState.ERROR
                //TODO add error reporting
                client = null
            }
            delay(config.reconnectionDelay)
        }
    }

    override suspend fun stop() {
        client?.disconnect()
        client = null
    }

    override suspend fun readTag(address: Meta): ValueWithTime<Meta>? = client?.let { client ->
        if (!client.isConnected) {
            lifecycleState = LifecycleState.STOPPED
            start()
            return null
        }
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
        public val reader: MetaRef<ModbusTagReader> by serializable<ModbusTagReader> {
            required()
        }

        public val unitId: MetaRef<Int> by int()
    }

}