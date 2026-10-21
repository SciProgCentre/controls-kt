package space.kscience.controls.tagtable

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import org.apache.plc4x.java.DefaultPlcDriverManager
import org.apache.plc4x.java.api.PlcConnection
import org.apache.plc4x.java.api.types.PlcValueType
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.isStarted
import space.kscience.controls.plc4x.Plc4xProperty
import space.kscience.controls.plc4x.throwOnFail
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.required

/**
 * A tag source that reads values from a PLC4X connection.
 */
public class Plc4xTagSource(
    private val config: Plc4xTagSourceConfig,
    private val scope: CoroutineScope,
    private val timeZone: TimeZone
) : TagSource {


    private var client: PlcConnection? = null

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
        val newClient = DefaultPlcDriverManager().getConnection(config.address)
        client = newClient

        connectionJob = scope.launch {
            newClient.connect()
            delay(config.reconnectionDelay)
            }
    }

    override suspend fun stop() {
        client?.close()
        client = null
    }

    override suspend fun readTag(address: Meta): ValueWithTime<Meta>? = client?.let { client ->

        if (!client.isConnected) {
            start()
            return null
        }

        require(client.metadata.isReadSupported) { "Read actions are not supported on connections" }

        val plcAddress = address[ColumnSpec.address] ?: error("PLC address is required")
        val plcValueType = address[ColumnSpec.plcValueType] ?: error("PLC value type is required")
        val name = address[ColumnSpec.name] ?: "@default"

        with(Plc4xProperty(plcAddress, plcValueType, name)) {
            val request = client.readRequestBuilder().request().build()
            val response = request.execute().await()
            response.throwOnFail()

            val time = response.getDateTime(name).toKotlinLocalDateTime().toInstant(timeZone)
            val value = response.readProperty()
            ValueWithTime(value, time)
        }
    }

    override fun validate(column: TagTableColumn) {
        if (!OpcTagSource.ColumnSpec.validate(column.config)) error("Invalid Plc4x address for column $column")
    }

    public object ColumnSpec : MetaSpec() {
        public val address: MetaRef<String> by string {
            required()
        }
        public val plcValueType: MetaRef<PlcValueType> by enum<PlcValueType> {
            required()
        }
        public val name: MetaRef<String> by string()
    }
}