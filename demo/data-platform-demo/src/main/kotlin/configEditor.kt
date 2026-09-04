package space.kscience.controls.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import space.kscience.controls.constructor.ConstructorDeviceConfiguration
import space.kscience.controls.demo.visual.DeviceConfigurator
import space.kscience.controls.tagtable.TagTableConfiguration
import space.kscience.controls.tagtable.TagTablePlugin
import space.kscience.controls.utilities.ControlsUtilitiesPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.SlfLogManager
import space.kscience.dataforge.io.IOPlugin
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

/**
 * Load [TagTableConfiguration] from a JSON file.
 */
@OptIn(ExperimentalSerializationApi::class)
public fun loadTagTableConfiguration(path: Path): TagTableConfiguration = path.inputStream().use {
    json.decodeFromStream(TagTableConfiguration.serializer(), it)
}

/**
 * Load [ConstructorDeviceConfiguration] from a JSON file.
 */
@OptIn(ExperimentalSerializationApi::class)
public fun loadDeviceConfiguration(path: Path): ConstructorDeviceConfiguration = path.inputStream().use {
    json.decodeFromStream(ConstructorDeviceConfiguration.serializer(), it)
}

// IMPORTANT: run in blocking mode
fun main(args: Array<String>) {
    val context = Context {
        plugin(IOPlugin)
        plugin(TagTablePlugin)
        plugin(ControlsUtilitiesPlugin)
        plugin(SlfLogManager)
    }

    var tagTablePath: Path? = null
    var deviceConfigPath: Path? = null

    // Parse command line arguments if provided
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--tag-table", "-t" -> {
                if (i + 1 < args.size) {
                    tagTablePath = Path(args[++i])
                }
            }
            "--device-config", "--config", "-c" -> {
                if (i + 1 < args.size) {
                    deviceConfigPath = Path(args[++i])
                }
            }
            else -> {
                val p = Path(args[i])
                if (tagTablePath == null && args[i].contains("platform", ignoreCase = true)) {
                    tagTablePath = p
                } else if (deviceConfigPath == null && args[i].contains("device", ignoreCase = true)) {
                    deviceConfigPath = p
                } else if (tagTablePath == null) {
                    tagTablePath = p
                } else if (deviceConfigPath == null) {
                    deviceConfigPath = p
                }
            }
        }
        i++
    }

    val initialTagTable = tagTablePath?.takeIf { it.exists() }?.let {
        try {
            loadTagTableConfiguration(it)
        } catch (e: Exception) {
            println("Failed to load tag table from $it: ${e.message}")
            null
        }
    }

    val initialConfiguration = deviceConfigPath?.takeIf { it.exists() }?.let {
        try {
            loadDeviceConfiguration(it)
        } catch (e: Exception) {
            println("Failed to load device config from $it: ${e.message}")
            null
        }
    }

    //launch visualization app
    application {
        Window(onCloseRequest = {
            context.close()
            exitApplication()
        }, title = "Data Platform Demo") {
            MaterialTheme {
                DeviceConfigurator(
                    context = context,
                    initialConfiguration = initialConfiguration,
                    initialTagTable = initialTagTable,
                    initialConfigurationPath = deviceConfigPath,
                    initialTagTablePath = tagTablePath
                )
            }
        }
    }
}

fun main() {
    main(emptyArray())
}