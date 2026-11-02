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
import space.kscience.controls.tagtable.TagTable
import space.kscience.controls.tagtable.TagTableConfiguration
import space.kscience.controls.tagtable.TagTablePlugin
import space.kscience.controls.tagtable.export.sourceStateExportColumns
import space.kscience.controls.tagtable.export.TableExporter
import space.kscience.controls.utilities.ControlsUtilitiesPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.SlfLogManager
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.plus
import kotlin.io.path.inputStream
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Clock

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

private fun ConstructorDeviceConfiguration.tagProperties(deviceName: Name): Map<Pair<Name, String>, String> = buildMap {
    properties.forEach { (propertyName, stateConfiguration) ->
        if (stateConfiguration.type == TagTable.TAG_TABLE_FACTORY_TYPE) {
            stateConfiguration.parameters[TagTable.ValueFactorySpec.tag]?.let { tag ->
                put(deviceName to propertyName, tag)
            }
        }
    }
    devices.forEach { (childName, configuration) ->
        putAll(configuration.tagProperties(deviceName + Name.of(childName)))
    }
}


// IMPORTANT: run in blocking mode
fun main() {
    val context = Context {
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
    val tagTable = tagTablePlugin.install(configuration, "table")

    // monitor changes
    tagTable.monitorDeviceChanges()


    //read device config
    val deviceConfig: ConstructorDeviceConfiguration =
        platformDataDirectory.resolve("device-config.json").inputStream().use {
            json.decodeFromStream(ConstructorDeviceConfiguration.serializer(), it)
        }

    //setup devices from config
    val devices = deviceManager.install("devices", deviceConfig)

    val exporter = TableExporter(tagTablePlugin.storage, configuration)
    val tagProperties = deviceConfig.tagProperties(Name.of("devices"))

    //launch visualization app
    application {
        Window(onCloseRequest = {
            context.close()
            exitApplication()
        }, title = "Data Platform Demo") {
            MaterialTheme {
                DeviceVisualisation(
                    hub = devices,
                    exporter = exporter,
                    tagByProperty = tagProperties,
                    sourceStateColumns = configuration.sourceStateExportColumns(),
                    exportStartTime = exportStartTime,
                )
            }
        }
    }


}