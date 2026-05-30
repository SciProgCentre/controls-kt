@file:OptIn(ExperimentalAtomicApi::class, ExperimentalSerializationApi::class)

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
import space.kscience.controls.constructor.DeviceConfiguration
import space.kscience.controls.dataplatform.DataPlatform
import space.kscience.controls.dataplatform.DataPlatformConfiguration
import space.kscience.controls.dataplatform.DataPlatformDevice
import space.kscience.controls.dataplatform.buildDeviceGroup
import space.kscience.controls.dataplatform.storage.RowsCompression
import space.kscience.controls.dataplatform.storage.storeData
import space.kscience.controls.demo.visual.DeviceVisualisation
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.manager.installNode
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.SlfLogManager
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.Meta
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.inputStream
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


// IMPORTANT: run in blocking mode
fun main() {
    val context = Context {
        plugin(IOPlugin)
        plugin(DeviceManager)
        plugin(SlfLogManager)
    }
    val deviceManager = context.request(DeviceManager)


    //setup data sources and data config
    val platformDataDirectory = deviceManager.setupPlatformTestStand(
        numberOfOpcDevices = 6,
        numberOfModbusDevices = 4,
        propertiesPerDevice = 30
    )

    //read platform config
    val configuration = platformDataDirectory.resolve("platform-config.json").inputStream().use {
        json.decodeFromStream(DataPlatformConfiguration.serializer(), it)
    }


    val platform = DataPlatform(context, configuration)

    //setup platform device (optional)
    val platformDevice = DataPlatformDevice(platform)
    deviceManager.install("platform", platformDevice)

    // monitor changes
    platformDevice.monitorDeviceChanges()


    //read device config
    val deviceConfig = platformDataDirectory.resolve("device-config.json").inputStream().use {
        json.decodeFromStream(DeviceConfiguration.serializer(), it)
    }
    //setup devices from config
    val devices = platform.buildDeviceGroup(deviceConfig)
    deviceManager.installNode("devices", devices)


//    val allDescriptors = platformDevice.propertyDescriptors

    // start storing data event from the platform
    platform.storeData(
        directory = platformDataDirectory,
        readInterval = 1.seconds,
        maxDuration = 30.seconds,
        compression = RowsCompression(skipUnchangedRows = true, skipUnchangedValues = true, numericDelta = 0.05)
    )


    //launch visualization app
    application {
        Window(onCloseRequest = {
            context.close()
            exitApplication()
        }, title = "Data Platform Demo") {
            MaterialTheme {
                DeviceVisualisation(devices)
            }
        }
    }


}