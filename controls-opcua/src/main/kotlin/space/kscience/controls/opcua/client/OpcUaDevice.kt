package space.kscience.controls.opcua.client

import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import space.kscience.controls.api.Device
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MetaSerializer
import space.kscience.dataforge.meta.asValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant


/**
 * An OPC-UA device backed by Eclipse Milo client
 */
public interface OpcUaDevice : Device {
    /**
     * The OPC-UA client initialized on first use
     */
    public val client: OpcUaClient
}

public suspend inline fun <reified T : Any> OpcUaClient.readValueWithTime(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    maxAge: Double = 500.0,
    clock: Clock = Clock.System
): ValueWithTime<T> {
    val data: DataValue = readValuesAsync(maxAge, TimestampsToReturn.Server, listOf(nodeId)).await().first()
    val time: Instant = data.serverTime?.javaInstant?.toKotlinInstant() ?: clock.now()
    val meta: Meta = when (val content = data.value.value) {
        is T -> return ValueWithTime(content, time)
        is Meta -> content
        is ExtensionObject -> content.decode(dynamicEncodingContext) as Meta
        is String -> Json.decodeFromString(MetaSerializer, content)
        is Number -> Meta(content)
        is Boolean -> Meta(content)
        is DoubleArray -> Meta(content.asValue())
        is IntArray -> Meta(content.asValue())
        else -> error("Incompatible OPC property value $content")
    }

    val res: T = converter.read(meta)
    return ValueWithTime(res, time)
}

/**
 * Read OPC-UA value with timestamp
 * @param T the type of property to read. The value is coerced to it.
 */
public suspend inline fun <reified T : Any> OpcUaDevice.readOpcWithTime(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    maxAge: Double = 500.0
): ValueWithTime<T> = client.readValueWithTime(nodeId, converter, maxAge)

/**
 * Read and coerce value from OPC-UA
 */
public suspend inline fun <reified T : Any> OpcUaDevice.readOpc(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    maxAge: Double = 500.0
): T = client.readValueWithTime(nodeId, converter, maxAge).value

public suspend inline fun <reified T> OpcUaDevice.writeOpc(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    value: T
): StatusCode {

    val variant: Variant = when (value) {
        is Number -> Variant(value)
        is Boolean -> Variant(value)
        is DoubleArray -> Variant(value)
        is IntArray -> Variant(value)
        else -> {
            val meta = converter.convert(value)
            Variant.ofString(Json.encodeToString(MetaSerializer, meta))
        }
    }


    //TODO convert Meta to proper variants

    return client.writeValuesAsync(listOf(nodeId), listOf(DataValue(variant))).await().first()
}


/**
 * A device-bound OPC-UA property. Does not trigger device properties change.
 */
public inline fun <reified T : Any> OpcUaDevice.opc(
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