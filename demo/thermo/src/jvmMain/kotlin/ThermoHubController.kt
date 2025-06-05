package center.sciprog.controls.demo.thermo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import space.kscience.controls.api.DeviceHub
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.opcua.server.serveDevices


fun DeviceHub.serveOpc(scope: CoroutineScope): OpcUaServer {

    val opcUaServer: OpcUaServer = OpcUaServer {
        setApplicationName(LocalizedText.english("center.sciprog.controls.thermo"))

        endpoint {
            setBindPort(9091)
        }
    }

    opcUaServer.serveDevices(scope, this)
    opcUaServer.startup()


    scope.coroutineContext[Job]?.invokeOnCompletion {
        opcUaServer.shutdown()
    }

    return opcUaServer
}