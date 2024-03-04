package space.kscience.controls.opcua.client

import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import space.kscience.controls.api.Device
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MetaSerializer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


/**
 * An OPC-UA device backed by Eclipse Milo client
 */
public interface OpcUaDevice : Device {
    /**
     * The OPC-UA client initialized on first use
     */
    public val client: OpcUaClient
}

/**
 * Read OPC-UA value with timestamp
 * @param T the type of property to read. The value is coerced to it.
 */
public suspend inline fun <reified T: Any> OpcUaDevice.readOpcWithTime(
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

    val res: T = converter.read(meta)
    return res to time
}

/**
 * Read and coerce value from OPC-UA
 */
public suspend inline fun <reified T> OpcUaDevice.readOpc(
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

    return converter.readOrNull(meta) ?: error("Meta $meta could not be converted to ${T::class}")
}

public suspend inline fun <reified T> OpcUaDevice.writeOpc(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    value: T
): StatusCode {
    val meta = converter.convert(value)
    return client.writeValue(nodeId, DataValue(Variant(meta))).await()
}


/**
 * A device-bound OPC-UA property. Does not trigger device properties change.
 */
public inline fun <reified T> OpcUaDevice.opc(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    magAge: Double = 500.0
): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = runBlocking {
        readOpc(nodeId, converter, magAge)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        launch {
            writeOpc(nodeId, converter, value)
        }
    }
}

/**
 * Register a mutable OPC-UA based [Double] property in a device spec
 */
public fun OpcUaDevice.opcDouble(
    nodeId: NodeId,
    magAge: Double = 1.0
): ReadWriteProperty<Any?, Double> = opc<Double>(nodeId, MetaConverter.double, magAge)

/**
 * Register a mutable OPC-UA based [Int] property in a device spec
 */
public fun OpcUaDevice.opcInt(
    nodeId: NodeId,
    magAge: Double = 1.0
): ReadWriteProperty<Any?, Int> = opc(nodeId, MetaConverter.int, magAge)

/**
 * Register a mutable OPC-UA based [String] property in a device spec
 */
public fun OpcUaDevice.opcString(
    nodeId: NodeId,
    magAge: Double = 1.0
): ReadWriteProperty<Any?, String> = opc(nodeId, MetaConverter.string, magAge)