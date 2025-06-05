package center.sciprog.controls.demo.thermo

import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.opcua.server.serveDevices
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.request


class ThermoHubController(
    val deviceManager: DeviceManager,
    val opcUaServer: OpcUaServer,
    val sensorHub: ThermoSensorHub
) : ContextAware, AutoCloseable {

    override val context: Context get() = deviceManager.context

    fun start() {
        opcUaServer.startup()
        opcUaServer.serveDevices(deviceManager)
    }

    override fun close() {
        opcUaServer.shutdown()
    }

}

fun ThermoHubController(sensorHub: ThermoSensorHub): ThermoHubController {

    val context = sensorHub.context
    val deviceManager = context.request(DeviceManager)

    val opcUaServer: OpcUaServer = OpcUaServer {
        setApplicationName(LocalizedText.english("center.sciprog.controls.thermo"))

        endpoint {
            setBindPort(9091)
        }
    }

    opcUaServer.serveDevices(deviceManager)
//    opcUaServer.startup()


    return ThermoHubController(deviceManager, opcUaServer, sensorHub)
}