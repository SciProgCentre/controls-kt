package space.kscience.controls.opcua.client

import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import space.kscience.controls.api.Device
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaSerializer
import space.kscience.dataforge.meta.transformations.MetaConverter


/**
 * An OPC-UA device backed by Eclipse Milo client
 */
public interface MiloDevice : Device {
    /**
     * The OPC-UA client initialized on first use
     */
    public val client: OpcUaClient

    override fun close() {
        client.disconnect()
        super.close()
    }
}

/**
 * Read OPC-UA value with timestamp
 * @param T the type of property to read. The value is coerced to it.
 */
public suspend inline fun <reified T: Any> MiloDevice.readOpcWithTime(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    magAge: Double = 500.0
): Pair<T, DateTime> {
    val data = client.readValue(magAge, TimestampsToReturn.Server, nodeId).await()
    val time = data.serverTime ?: error("No server time provided")
    val meta: Meta = when (val content = data.value.value) {
        is T -> return content to time
        is Meta -> content
        is ExtensionObject -> content.decode(client.dynamicSerializationContext) as Meta
        else -> error("Incompatible OPC property value $content")
    }

    val res: T = converter.metaToObject(meta) ?: error("Meta $meta could not be converted to ${T::class}")
    return res to time
}

/**
 * Read and coerce value from OPC-UA
 */
public suspend inline fun <reified T> MiloDevice.readOpc(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    magAge: Double = 500.0
): T {
    val data: DataValue = client.readValue(magAge, TimestampsToReturn.Neither, nodeId).await()

    val content = data.value.value
    if(content is T) return  content
    val meta: Meta = when (content) {
        is Meta -> content
        //Always decode string as Json meta
        is String -> Json.decodeFromString(MetaSerializer, content)
        is Number -> Meta(content)
        is Boolean -> Meta(content)
        //content is ExtensionObject -> (content as ExtensionObject).decode(client.dynamicSerializationContext) as Meta
        else -> error("Incompatible OPC property value $content")
    }

    return converter.metaToObject(meta) ?: error("Meta $meta could not be converted to ${T::class}")
}

public suspend inline fun <reified T> MiloDevice.writeOpc(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    value: T
): StatusCode {
    val meta = converter.objectToMeta(value)
    return client.writeValue(nodeId, DataValue(Variant(meta))).await()
}