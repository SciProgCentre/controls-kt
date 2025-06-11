package center.sciprog.controls.demo.thermo

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import space.kscience.controls.api.DeviceHub
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.opcua.server.serveDevices
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request

fun DeviceHub.serveOpc(
    scope: CoroutineScope,
    port: Int = 9091,
): OpcUaServer {

    val opcUaServer: OpcUaServer = OpcUaServer {
        setApplicationName(LocalizedText.english("center.sciprog.controls.thermo"))

        endpoint {
            setBindPort(port)
        }
    }

    opcUaServer.serveDevices(scope, this)
    opcUaServer.startup()


    scope.coroutineContext[Job]?.invokeOnCompletion {
        opcUaServer.shutdown()
    }

    return opcUaServer
}

internal fun Context.setup(): ThermoSensorHub{

    val configuration: Map<String, ThermoSensorConfig> = generateTestConfig(
        numberOfUnits = 1
    )
    launchModbusSimulator(configuration)
    Thread.sleep(200)

    val modbusMaster = ModbusTCPMaster("127.0.0.1", 9090)
    modbusMaster.connect()

    val thermoHub = ModbusThermoSensorHub(request(DeviceManager), modbusMaster, configuration)

    thermoHub.serveOpc(this)

    coroutineContext[Job]?.invokeOnCompletion {
        modbusMaster.disconnect()
    }

    return thermoHub
}