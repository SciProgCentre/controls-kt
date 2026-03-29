@file:OptIn(ExperimentalAtomicApi::class)

package space.kscience.controls.demo

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import space.kscience.controls.api.onPropertyChange
import space.kscience.controls.dataplatform.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

fun main() {
    val context = Context {
        plugin(DeviceManager)
    }
    val deviceManager = context.request(DeviceManager)

    val numberOfOpcDevices = 4

    val numberOfModbusDevices = 2

    val propertiesPerDevice = 4

    val registryMap = TestDeviceRegistryMap(
        List(propertiesPerDevice) { NameToken("property", it.toString()).asName() }
    )

    deviceManager.setupTestDevices(
        propertiesPerDevice = propertiesPerDevice,
        numberOfOpcDevices = numberOfOpcDevices,
        numberOfModbusDevices = numberOfModbusDevices,
        registryMap = registryMap,
        scope = context
    )

    Thread.sleep(1000)

    val opcSourceName = "opc".asName()
    val modbusSourceName = "modbus".asName()

    val sources = mapOf(
        opcSourceName to OpcUaConfig("opc.tcp://localhost:9091"),
        modbusSourceName to ModbusTcpConfig("localhost", 9093)
    )

    val timerName = "default".asName()

    val timers = mapOf(timerName to FixedRateTimer(1.seconds))

    val platformProperties = buildMap<Name, PlatformProperty> {
        repeat(numberOfOpcDevices) { opcDeviceNum ->
            repeat(propertiesPerDevice) { propertyNum ->
                put(
                    key = "opc[${opcDeviceNum}].property[${propertyNum}]".parseAsName(),
                    value = OpcPlatformProperty(
                        source = opcSourceName,
                        timer = timerName,
                        nodeId = "ns=2;s=opc/device[$opcDeviceNum]/property[$propertyNum]"
                    )
                )
            }
        }

        repeat(numberOfModbusDevices) { modbusDeviceNum ->

            registryMap.keys.forEach { (name, key) ->
                put(
                    key = "modbus[${modbusDeviceNum}]".parseAsName() + name,
                    value = ModbusPlatformProperty(
                        source = modbusSourceName,
                        timer = timerName,
                        reader = ModbusDoubleReader,
                        address = key.address,
                        unitId = modbusDeviceNum + 1
                    )
                )
            }
        }
    }

    val configuration = DataPlatformConfiguration(
        sources = sources,
        timers = timers,
        properties = platformProperties
    )

    Path("platform-config.json").writeText(
        Json { prettyPrint = true }.encodeToString(DataPlatformConfiguration.serializer(), configuration)
    )

    val platformDevice = DataPlatformDevice(context, configuration)

    deviceManager.install("platform", platformDevice)

//    val allDescriptors = platformDevice.propertyDescriptors

    context.launch {

        val mutex = Mutex()
        val values = mutableMapOf<String, Meta>()

        platformDevice.onPropertyChange {
            mutex.withLock {
                values[property] = value
            }
        }

        while(isActive) {
            delay(1.seconds)
            mutex.withLock {
                println("Changed in a last second: ${values.size}")
                values.clear()
            }
        }
    }

    do{
        val str = readlnOrNull()
    } while (str != "exit")

    context.close()
}