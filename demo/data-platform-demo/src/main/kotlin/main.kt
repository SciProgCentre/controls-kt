@file:OptIn(ExperimentalSerializationApi::class)

package space.kscience.controls.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import space.kscience.controls.api.Device
import space.kscience.controls.api.onPropertyChange
import space.kscience.controls.constructor.ConstructorDeviceConfiguration
import space.kscience.controls.constructor.install
import space.kscience.controls.demo.visual.DeviceVisualisation
import space.kscience.controls.expressions.ControlsExpressionPlugin
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.storage.*
import space.kscience.controls.tagtable.OpcTagSourceConfig
import space.kscience.controls.tagtable.PlcTagTable
import space.kscience.controls.tagtable.TagTableConfiguration
import space.kscience.controls.tagtable.TagTablePlugin
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.controls.tagtable.storage.asDeviceMessageStorage
import space.kscience.controls.utilities.ControlsUtilitiesPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.SlfLogManager
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.plus
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

internal val json = Json { prettyPrint = true }

/**
 * Console monitoring of device updates
 */
private fun Device.monitorDeviceChanges(): Job = launch {

    val mutex = Mutex()
    val values = mutableMapOf<String, Meta>()

    this@monitorDeviceChanges.onPropertyChange(this) {
        mutex.withLock {
            values[property] = value
        }
    }

    launch {
        while (isActive) {
            measureTime {
                delay(1.seconds)
            }.let { println("Delay took ${it}") }
            mutex.withLock {
                if (values.isNotEmpty()) {
                    println("[${Clock.System.now()}] Changed in a last second: ${values.size}")
                    values.clear()
                }
            }
        }
    }
}

/**
 * Build a combined messages storage for tag and auxiliary messages using the storage paths in a tag-table configuration.
 */
suspend fun Context.readMessages(
    configuration: TagTableConfiguration,
): DeviceMessageStorage {
    val storage = request(ControlsStoragePlugin)

    val storageConfiguration = configuration.storage ?: error("Tag table storage is not configured")
    val messageStorageConfiguration = configuration.messageStorage ?: error("Message storage is not configured")
    val operations = if (storageConfiguration.separateMeta) {
        NativeFileEnvelopeOperations(storage.io)
    } else {
        SingleFileEnvelopeOperations(storage.io)
    }
    val tableDirectory = Path(storageConfiguration.path).createDirectories()
    val messageDirectory = Path(messageStorageConfiguration.path).createDirectories()

    // raw tag storage
    val index = TableStorageIndex(storage, tableDirectory, operations = operations)
    index.start()

    // auxiliary message store
    val messages = FileDeviceMessageStorage(
        directory = messageDirectory,
        maxMessagesPerFile = messageStorageConfiguration.maxMessagesPerFile,
    )

    return DeviceMessageStorage.merge(index.asDeviceMessageStorage(), messages)
}

/**
 * Describe the persisted state columns for every configured source.
 */
private fun TagTableConfiguration.sourceStateExportColumns(): List<ExportColumn> = sources.flatMap { (source, config) ->
    val device = Name.of(PlcTagTable.SOURCE_DEVICE_KEY, source)
    buildList {
        add(ExportColumn(device, "state", Name.of("lifeCycle")))
        if (config is OpcTagSourceConfig) {
            add(ExportColumn(device, "state", Name.of("sourceTimeOffset")))
        }
    }
}

// IMPORTANT: run in blocking mode
suspend fun main() {
    val context = Context("platform") {
        plugin(DeviceManager)
        plugin(IOPlugin)
        plugin(TagTablePlugin)
        plugin(ControlsExpressionPlugin)
        plugin(ControlsUtilitiesPlugin)
        plugin(SlfLogManager)
    }

    val tagTablePlugin = context.request(TagTablePlugin)

    val deviceManager = tagTablePlugin.deviceManager

    val dataDirectory = Path("data").apply { createDirectories() }

    //setup data sources and data config
    val platformDataDirectory = deviceManager.setupPlatformTestStand(
        dataDirectory = dataDirectory,
        numberOfOpcDevices = 6,
        numberOfModbusDevices = 4,
        propertiesPerDevice = 300
    )

    //read platform config
    val configuration: TagTableConfiguration = platformDataDirectory.resolve("platform-config.json").inputStream().use {
        json.decodeFromStream(TagTableConfiguration.serializer(), it)
    }


    val exportStartTime = Clock.System.now()
    val tagTable = tagTablePlugin.install(configuration)

    // monitor changes
    tagTable.monitorDeviceChanges()

    //read device config
    val deviceConfig: ConstructorDeviceConfiguration = platformDataDirectory
        .resolve("device-config.json")
        .inputStream().use {
            json.decodeFromStream(ConstructorDeviceConfiguration.serializer(), it)
        }

    //setup devices from config
    val devices = deviceManager.install("devices", deviceConfig)

    val columnTagMapping: Map<ExportColumn, ExportColumn> = buildMap {
        fun processDevice(deviceCfg: ConstructorDeviceConfiguration, prefix: Name) {
            deviceCfg.properties.forEach { (propertyName, propertyConfig) ->
                val tag = propertyConfig.attributes["tag"].string ?: return@forEach
                put(ExportColumn(prefix, propertyName), ExportColumn(Name.EMPTY, tag, exportColumnName = tag))
            }
            deviceCfg.devices.forEach { (deviceName, childDeviceCfg) ->
                processDevice(childDeviceCfg, prefix + deviceName)
            }
        }
        processDevice(deviceConfig, Name.EMPTY)
    }

    val storageConfig = platformDataDirectory.resolve("storage-config.json").inputStream().use {
        json.decodeFromStream(Meta.serializer(), it)
    }

    //create a child context for storage with pre-defined storagemapping
    val storageContext = context.buildContext("storage") {
        plugin(ControlsStoragePlugin, storageConfig)
    }

    // message storage for tag table and its auxiliary messages
    val tableMessageStorage = storageContext.readMessages(configuration)

    val storagePlugin = storageContext.request(ControlsStoragePlugin)

    storagePlugin.launchStorageJob(devices)


    //launch visualization app
    application {
        Window(onCloseRequest = {
            context.close()
            exitApplication()
        }, title = "Data Platform Demo") {
            MaterialTheme {
                DeviceVisualisation(
                    hub = devices,
                    messageStorage = DeviceMessageStorage.merge(
                        *storagePlugin.storageMapping.values.toTypedArray(),
                        tableMessageStorage
                    ),
                    exportColumnMapping = columnTagMapping,
                    sourceStateColumns = configuration.sourceStateExportColumns(),
                    exportStartTime = exportStartTime,
                )
            }
        }
    }


}