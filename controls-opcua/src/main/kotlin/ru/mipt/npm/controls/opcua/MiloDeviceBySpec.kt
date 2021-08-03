package ru.mipt.npm.controls.opcua

import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import ru.mipt.npm.controls.properties.DeviceBySpec
import ru.mipt.npm.controls.properties.DeviceSpec
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string

public open class MiloDeviceBySpec<D: MiloDeviceBySpec<D>>(
    spec: DeviceSpec<D>,
    context: Context = Global,
    meta: Meta = Meta.EMPTY
): MiloDevice, DeviceBySpec<D>(spec, context, meta) {

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