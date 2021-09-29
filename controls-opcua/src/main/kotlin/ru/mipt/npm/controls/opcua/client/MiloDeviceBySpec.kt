package ru.mipt.npm.controls.opcua.client

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import ru.mipt.npm.controls.properties.DeviceBySpec
import ru.mipt.npm.controls.properties.DeviceSpec
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

public open class MiloDeviceBySpec<D : MiloDeviceBySpec<D>>(
    spec: DeviceSpec<D>,
    context: Context = Global,
    meta: Meta = Meta.EMPTY
) : MiloDevice, DeviceBySpec<D>(spec, context, meta) {

    override val client: OpcUaClient by lazy {
        val endpointUrl = meta["endpointUrl"].string ?: error("Endpoint url is not defined")
        context.createMiloClient(endpointUrl).apply {
            connect().get()
        }
    }

    override fun close() {
        super<MiloDevice>.close()
        super<DeviceBySpec>.close()
    }
}

/**
 * A device-bound OPC-UA property. Does not trigger device properties change.
 */
public inline fun <reified T> MiloDeviceBySpec<*>.opc(
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

public inline fun <reified T> MiloDeviceBySpec<*>.opcDouble(
    nodeId: NodeId,
    magAge: Double = 1.0
): ReadWriteProperty<Any?, Double> = opc(nodeId, MetaConverter.double, magAge)

public inline fun <reified T> MiloDeviceBySpec<*>.opcInt(
    nodeId: NodeId,
    magAge: Double = 1.0
): ReadWriteProperty<Any?, Int> = opc(nodeId, MetaConverter.int, magAge)

public inline fun <reified T> MiloDeviceBySpec<*>.opcString(
    nodeId: NodeId,
    magAge: Double = 1.0
): ReadWriteProperty<Any?, String> = opc(nodeId, MetaConverter.string, magAge)