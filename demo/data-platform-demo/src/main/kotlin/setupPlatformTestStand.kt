package space.kscience.controls.demo

import space.kscience.controls.constructor.DeviceConfiguration
import space.kscience.controls.dataplatform.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName
import space.kscience.dataforge.names.plus
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

/**
 * Sets up a platform test stand with the specified number of OPC and Modbus devices.
 * Returns the path to the data directory where configuration files are stored.
 */
internal fun DeviceManager.setupPlatformTestStand(
    numberOfOpcDevices: Int,
    numberOfModbusDevices: Int,
    propertiesPerDevice: Int,
): Path {
    val opcSourceName = "opc"
    val modbusSourceName = "modbus"

    val sources = mapOf(
        opcSourceName to OpcUaConfig("opc.tcp://localhost:9091"),
        modbusSourceName to ModbusTcpConfig("localhost", 9093)
    )

    val timerName = "default"

    val timers = mapOf(timerName to FixedRateTimer(1.seconds))

    val registryMap = TestDeviceRegistryMap(
        List(propertiesPerDevice) { "property[$it]" }
    )

    setupTestDevices(
        propertiesPerDevice = propertiesPerDevice,
        numberOfOpcDevices = numberOfOpcDevices,
        numberOfModbusDevices = numberOfModbusDevices,
        registryMap = registryMap,
        scope = context
    )

    Thread.sleep(1000)

    val platformProperties = buildMap<Name, TagTableColumn> {
        repeat(numberOfOpcDevices) { opcDeviceNum ->
            repeat(propertiesPerDevice) { propertyNum ->
                put(
                    key = "opc[${opcDeviceNum}].property[${propertyNum}]".parseAsName(),
                    value = OpcTagTableColumn(
                        source = opcSourceName,
                        timer = timerName,
                        nodeId = "ns=2;s=device[$opcDeviceNum]/property[$propertyNum]"
                    )
                )
            }
        }

        repeat(numberOfModbusDevices) { modbusDeviceNum ->

            registryMap.keys.forEach { (name, key) ->
                put(
                    key = "modbus[${modbusDeviceNum}]".parseAsName() + name,
                    value = ModbusTagTableColumn(
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

    val dataDirectory = Path("data")
    dataDirectory.createDirectories()

    val configuration = PlcTableConfiguration(
        sources = sources,
        timers = timers,
        properties = platformProperties.mapKeys { it.key.toString() },
        storage = TagTableStorageConfiguration(
            path = dataDirectory.toString(),
            readInterval = 1.seconds,
            maxDuration = 30.seconds,
            compression = RowsCompression(skipUnchangedRows = true, skipUnchangedValues = true, numericDelta = 0.05)
        )
    )


    dataDirectory.resolve("platform-config.json").writeText(
        json.encodeToString(PlcTableConfiguration.serializer(), configuration)
    )

    val deviceConfiguration = createDeviceConfiguration(configuration)
    dataDirectory.resolve("device-config.json").writeText(
        json.encodeToString(
            DeviceConfiguration.serializer(),
            deviceConfiguration
        )
    )

    return dataDirectory
}