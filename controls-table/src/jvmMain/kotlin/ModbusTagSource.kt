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
import org.slf4j.LoggerFactory
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

    private fun setState(block: MutableMeta.() -> Unit) {
        state.value = SourceState(Meta(block))
    }

    override var lifecycleState: LifecycleState
        get() = state.value.lifeCycleState
        private set(value) {
            setState { set(SourceState.lifeCycle, value) }
        }


    private var connectionJob: Job? = null

    override suspend fun start(): Unit = synchronized(this) {
        if (lifecycleState.isStarted()) return
        if (connectionJob?.isActive == true) return

        connectionJob = scope.launch {
            lifecycleState = LifecycleState.STARTING
            try {
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

                newClient.connect()
                logger.info("Modbus client connected")
                lifecycleState = LifecycleState.STARTED
            } catch (e: Exception) {
                logger.error("Error during Modbus client connection: ${e.message}")
                setState {
                    set(SourceState.lifeCycle, LifecycleState.ERROR)
                    set(SourceState.error, "${e.javaClass.simpleName}: ${e.message}")
                }
                client = null
            }
            delay(config.reconnectionDelay)
        }
    }

    override suspend fun stop() {
        client?.disconnect()
        client = null
    }

    override suspend fun readTag(address: Meta): ValueWithTime<Meta>? {
        if (client == null) {
            logger.info("Modbus client is not connected, attempting to reconnect")
            start()
        }
        return client?.let { client ->
            val modbusAddress = address[ColumnSpec.address] ?: error("Modbus address is not defined in $address")
            val reader = address[ColumnSpec.reader] ?: error("Modbus read is not defined")
            val unitId = address[ColumnSpec.unitId] ?: 1

            val res = reader.read(client, unitId, modbusAddress)

            ValueWithTime(res, clock.now())
        }
    }

    override fun validate(column: TagTableColumn) {
        if (!ColumnSpec.validate(column.config)) error("Invalid Modbus address for column $column")
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

    public companion object {
        private val logger = LoggerFactory.getLogger(ModbusTagSource::class.java)
    }
}
