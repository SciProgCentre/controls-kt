package ru.mipt.npm.controls.opcua

import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import ru.mipt.npm.controls.api.Device
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


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

public inline fun <reified T> MiloDevice.opc(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    magAge: Double = 500.0
): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val data = client.readValue(magAge, TimestampsToReturn.Server, nodeId).get()
        val meta: Meta = when (val content = data.value.value) {
            is T -> return content
            content is Meta -> content as Meta
            content is ExtensionObject -> (content as ExtensionObject).decode(client.dynamicSerializationContext) as Meta
            else -> error("Incompatible OPC property value $content")
        }

        return converter.metaToObject(meta) ?: error("Meta $meta could not be converted to ${T::class}")
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val meta = converter.objectToMeta(value)
        client.writeValue(nodeId, DataValue(Variant(meta)))
    }
}

public inline fun <reified T> MiloDevice.opcDouble(
    nodeId: NodeId,
    magAge: Double = 1.0
): ReadWriteProperty<Any?, Double> = opc(nodeId, MetaConverter.double, magAge)

public inline fun <reified T> MiloDevice.opcInt(
    nodeId: NodeId,
    magAge: Double = 1.0
): ReadWriteProperty<Any?, Int> = opc(nodeId, MetaConverter.int, magAge)

public inline fun <reified T> MiloDevice.opcString(
    nodeId: NodeId,
    magAge: Double = 1.0
): ReadWriteProperty<Any?, String> = opc(nodeId, MetaConverter.string, magAge)