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
            delay(1.seconds)
            mutex.withLock {
                if (values.isNotEmpty()) {
                    println("Changed in a last second: ${values.size}")
                    values.clear()
                }
            }
        }
    }
}

/**
 * Build a table exporter using the storage paths in a tag-table configuration.
 */
public suspend fun TagTablePlugin.readMessages(
    configuration: TagTableConfiguration,
): DeviceMessageStorage {
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
    val context = Context {
        plugin(DeviceManager)
        plugin(IOPlugin)
        plugin(TagTablePlugin)
        plugin(ControlsExpressionPlugin)
        plugin(ControlsUtilitiesPlugin)
        plugin(SlfLogManager)
    }

    val tagTablePlugin = context.request(TagTablePlugin)

    val deviceManager = tagTablePlugin.deviceManager


    //setup data sources and data config
    val platformDataDirectory = deviceManager.setupPlatformTestStand(
        numberOfOpcDevices = 6,
        numberOfModbusDevices = 4,
        propertiesPerDevice = 30
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
                val tag = propertyConfig.parameters["tag"].string ?: return@forEach
                put(ExportColumn(prefix, propertyName), ExportColumn(Name.EMPTY, tag, exportColumnName = tag))
            }
            deviceCfg.devices.forEach {(deviceName,childDeviceCfg)->
                processDevice(childDeviceCfg, prefix + deviceName)
            }
        }
        processDevice(deviceConfig, Name.EMPTY)
    }

    val messageStorage = tagTablePlugin.readMessages(configuration)

    //launch visualization app
    application {
        Window(onCloseRequest = {
            context.close()
            exitApplication()
        }, title = "Data Platform Demo") {
            MaterialTheme {
                DeviceVisualisation(
                    hub = devices,
                    messageStorage = messageStorage,
                    exportColumnMapping = columnTagMapping,
                    sourceStateColumns = configuration.sourceStateExportColumns(),
                    exportStartTime = exportStartTime,
                )
            }
        }
    }


}