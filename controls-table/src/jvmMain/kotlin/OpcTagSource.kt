package space.kscience.controls.tagtable

import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import space.kscience.controls.opcua.client.readMetaWithTime
import space.kscience.controls.opcua.client.readMultipleMetaWithTime
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.required
import kotlin.time.DurationUnit

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
    private val config: OpcTagSourceConfig
) : TagSource {

    private var client: OpcUaClient? = null

    override suspend fun open() {
        client = OpcUaClient.create(config.host).apply {
            connect()
        }
    }

    override fun close() {
        client?.disconnect()
        client = null
    }

    override suspend fun readTag(address: Meta): ValueWithTime<Meta>? = client?.let { client ->
        val nodeId = address[ColumnSpec.nodeId] ?: return null
        return client.readMetaWithTime(NodeId.parse(nodeId))
    }

    override suspend fun readMultipleTags(
        addresses: Collection<Pair<String, Meta>>
    ): Map<String, ValueWithTime<Meta>>? = client?.let { client ->
        val keys = addresses.map { it.first }
        val nodeIds = addresses.map { NodeId.parse(it.second[ColumnSpec.nodeId] ?: error("nodeId is not defined in OPC column config")) }

        val samples = client.readMultipleMetaWithTime(
            nodeIds,
            config.maxAge.toDouble(DurationUnit.MILLISECONDS)
        )

        return keys.zip(samples).associate { (key, sample) ->
            key to sample
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