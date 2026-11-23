package space.kscience.controls.demo

import space.kscience.controls.constructor.ConstructorDeviceConfiguration
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.storage.FileDeviceMessageStorage
import space.kscience.controls.tagtable.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.put
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName
import space.kscience.dataforge.names.plus
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

/**
 * Sets up a platform test stand with the specified number of OPC and Modbus devices.
 * Returns the path to the data directory where configuration files are stored.
 */
internal fun DeviceManager.setupPlatformTestStand(
    dataDirectory: Path,
    numberOfOpcDevices: Int,
    numberOfModbusDevices: Int,
    propertiesPerDevice: Int,
    maxMessagesPerFile: Int = 2000
): Path {
    val opcSourceName = "opc"
    val modbusSourceName = "modbus"

    val sources = mapOf(
        opcSourceName to OpcTagSourceConfig("opc.tcp://localhost:9091"),
        modbusSourceName to ModbusTcpTagSourceConfig("localhost", 9093)
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
                    value = TagTableColumn.opc(
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
                    value = TagTableColumn.modbus(
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

    val configuration = TagTableConfiguration(
        sources = sources,
        timers = timers,
        tags = platformProperties.mapKeys { it.key.toString() },
        storage = TagTableStorageConfiguration(
            path = dataDirectory.resolve("tags").apply { createDirectories() }.toString(),
            readInterval = 1.seconds,
            maxDuration = 30.seconds,
            compression = RowsCompression(skipUnchangedRows = true, skipUnchangedValues = true, numericDelta = 0.05),
            separateMeta = false,
            withTagState = true,
//            rowsConverterType = PlainRowsEnvelopeConverter.meta.envelopeType
        ),
        messageStorage = MessageStorageConfiguration(
            path = dataDirectory.resolve("tableMessages").apply { createDirectories() }.toString()
        )
    )


    dataDirectory.resolve("platform-config.json").writeText(
        json.encodeToString(TagTableConfiguration.serializer(), configuration)
    )

    val deviceConfiguration = createDeviceConfiguration(configuration)
    dataDirectory.resolve("device-config.json").writeText(
        json.encodeToString(
            ConstructorDeviceConfiguration.serializer(),
            deviceConfiguration
        )
    )

    val storageConfiguration = Meta {
        "storage" put {
            "file" put {
                "type" put "file"
                set(FileDeviceMessageStorage.directory, "data/deviceMessages")
                set(FileDeviceMessageStorage.maxMessagesPerFile, maxMessagesPerFile)
                set(FileDeviceMessageStorage.compressHistory, true)
            }
        }
    }

    dataDirectory.resolve("storage-config.json").writeText(
        json.encodeToString(Meta.serializer(), storageConfiguration)
    )

//    val alarmConfig = createAlarmConfig(deviceConfiguration)
//    dataDirectory.resolve("alarm-config.json").writeText(
//        json.encodeToString(
//            ConstructorDeviceConfiguration.serializer(),
//            alarmConfig
//        )
//    )

    return dataDirectory
}