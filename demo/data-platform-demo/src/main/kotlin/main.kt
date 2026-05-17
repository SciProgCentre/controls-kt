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
import space.kscience.controls.dataplatform.storage.RowsCompression
import space.kscience.controls.dataplatform.storage.launchStorageProcess
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.SlfLogManager
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

// IMPORTANT: run in blocking mode
fun main() {
    val context = Context {
        plugin(DeviceManager)
        plugin(SlfLogManager)
    }
    val deviceManager = context.request(DeviceManager)

    val numberOfOpcDevices = 6

    val numberOfModbusDevices = 4

    val propertiesPerDevice = 30

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

    val opcSourceName = "opc"
    val modbusSourceName = "modbus"

    val sources = mapOf(
        opcSourceName to OpcUaConfig("opc.tcp://localhost:9091"),
        modbusSourceName to ModbusTcpConfig("localhost", 9093)
    )

    val timerName = "default"

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
        properties = platformProperties.mapKeys { it.key.toString() }
    )

    Path("data/platform-config.json").writeText(
        Json { prettyPrint = true }.encodeToString(DataPlatformConfiguration.serializer(), configuration)
    )

    val platform = DataPlatform(context, configuration)

    val platformDevice = DataPlatformDevice(platform)

    deviceManager.install("platform", platformDevice)

    deviceManager.installFromConfiguration(platform, configuration, "devices")




//    val allDescriptors = platformDevice.propertyDescriptors

    val dataDirectory = Path("data").also {
        it.createDirectories()
    }

    //store all data from the platform
    val storageJob = platform.launchStorageProcess(
        directory = dataDirectory,
        readInterval = 1.seconds,
        maxDuration = 30.seconds,
        compression = RowsCompression(skipUnchangedRows = true, skipUnchangedValues = true, numericDelta = 0.05)
    )

    // monitor changes
    context.launch {

        val mutex = Mutex()
        val values = mutableMapOf<String, Meta>()

        platformDevice.onPropertyChange(this) {
            mutex.withLock {
                values[property] = value
            }
        }

        launch {
            while (isActive) {
                delay(1.seconds)
                mutex.withLock {
                    println("Changed in a last second: ${values.size}")
                    values.clear()
                }
            }
        }
    }

    do {
        val str = readlnOrNull()
    } while (str != "exit")

    context.close()
}