package space.kscience.controls.tagtable

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.isStarted
import space.kscience.controls.opcua.client.readMetaWithTime
import space.kscience.controls.opcua.client.readMultipleMetaWithTime
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.required
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.toKotlinInstant


/**
 * An implementation of [TagSource] for interacting with OPC-UA sources of tag values.
 *
 * This class enables connecting to an OPC-UA server, reading tag values (single or multiple),
 * and validating the structure of columns against OPC-UA addressing conventions.
 *
 * @constructor Creates an instance of `OpcTagSource` with the given configuration
 * @param config The configuration object defining OPC-UA host and properties like maximum age
 */
public class OpcTagSource(
    private val config: OpcTagSourceConfig,
    private val scope: CoroutineScope,
    private val clock: Clock
) : TagSource {

    private var client: OpcUaClient? = null

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

            val newClient = OpcUaClient.create(config.host)
            client = newClient
            lifecycleState = LifecycleState.STARTING

            newClient.connectAsync().whenComplete { openClient: OpcUaClient, throwable ->
                if (throwable != null) {
                    lifecycleState = LifecycleState.ERROR
                    client = null
                } else {
                    lifecycleState = LifecycleState.STARTED

                    //update server time offset
                    val serverStatusNode = openClient.addressSpace.serverNode.serverStatusNode
                    val state = serverStatusNode.state
                    //TODO process state errors
                    val serverTime = serverStatusNode.currentTime

                    //write time offset between server and client
                    val timeDelta = clock.now() - serverTime.javaInstant.toKotlinInstant()
                    updateState {
                        set(SourceState.sourceTimeOffset, timeDelta.toString())
                    }
                }
            }.await()

            delay(config.reconnectionDelay)
        }
    }

    override suspend fun stop() {
        client?.disconnect()
        client = null
    }

    override suspend fun readTag(address: Meta): ValueWithTime<Meta>? = client?.let { client ->
        val nodeId = address[ColumnSpec.nodeId] ?: return null
        return client.readMetaWithTime(NodeId.parse(nodeId))
    }

    override suspend fun readMultipleTags(
        addresses: Collection<Pair<String, Meta>>
    ): Map<String, Result<ValueWithTime<Meta>>>? = client?.let { client ->
        val keys = addresses.map { it.first }
        val nodeIds = addresses.map {
            NodeId.parse(
                it.second[ColumnSpec.nodeId] ?: error("nodeId is not defined in OPC column config")
            )
        }

        val samples = client.readMultipleMetaWithTime(
            nodeIds,
            config.maxAge.toDouble(DurationUnit.MILLISECONDS)
        )

        return keys.zip(samples).associate { (key, sample) ->
            key to Result.success(sample)
        }
    }

    override fun validate(column: TagTableColumn) {
        if (!ColumnSpec.validate(column.config)) error("Invalid OPC address for column $column")
    }


    public object ColumnSpec : MetaSpec() {
        public val nodeId: MetaRef<String> by string {
            required()
        }
    }
}