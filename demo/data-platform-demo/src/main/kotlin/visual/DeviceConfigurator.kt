/*
 * LLM generated code: Main Device Scheme Visual Configurator Composable connecting Toolbar, Canvas, Sidebar, Inspector, and Bottom Panel.
 */
package space.kscience.controls.demo.visual

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import space.kscience.controls.constructor.ConstructorDeviceConfiguration
import space.kscience.controls.demo.visual.configurator.*
import space.kscience.controls.tagtable.TagTableConfiguration
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.Name
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Main Device Scheme Visual Configurator component.
 *
 * Provides a node-and-wire visual canvas and inspector to design, wire, and export
 * [ConstructorDeviceConfiguration] device schemes with tag mappings from [TagTableConfiguration].
 */
@Composable
fun DeviceConfigurator(
    context: Context,
    initialConfiguration: ConstructorDeviceConfiguration? = null,
    initialTagTable: TagTableConfiguration? = null,
    initialConfigurationPath: Path? = null,
    initialTagTablePath: Path? = null,
    modifier: Modifier = Modifier
) {
    val model = remember(context) {
        val modelInstance = DeviceConfiguratorModel(
            context = context,
            initialConfiguration = initialConfiguration,
            initialTagTable = initialTagTable
        )
        if (initialTagTable == null && initialTagTablePath != null && initialTagTablePath.exists()) {
            try {
                modelInstance.importTagTableFromFile(initialTagTablePath)
            } catch (e: Exception) {
                // Ignore fallback failure
            }
        }
        if (initialConfiguration == null && initialConfigurationPath != null && initialConfigurationPath.exists()) {
            try {
                modelInstance.importSchemeFromFile(initialConfigurationPath)
            } catch (e: Exception) {
                // Ignore fallback failure
            }
        }
        modelInstance
    }

    var showImportTagTableDialog by remember { mutableStateOf(false) }
    var showOpenSchemeDialog by remember { mutableStateOf(false) }
    var showExportSchemeDialog by remember { mutableStateOf(false) }
    var exportJsonContent by remember { mutableStateOf("") }

    var addDeviceParentPath by remember { mutableStateOf<Name?>(null) }
    var addPropertyDevicePath by remember { mutableStateOf<Name?>(null) }
    var addTemplateInfo by remember { mutableStateOf<Triple<Name, String, MetaDescriptor?>?>(null) }

    var bottomPanelExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Navigation Toolbar
            ConfiguratorToolbar(
                model = model,
                onImportTagTableClick = { showImportTagTableDialog = true },
                onOpenConfigClick = { showOpenSchemeDialog = true },
                onExportConfigClick = {
                    exportJsonContent = model.exportSchemeJson()
                    showExportSchemeDialog = true
                },
                onToggleDiagnosticsClick = {
                    bottomPanelExpanded = !bottomPanelExpanded
                }
            )

            // 2. Main Middle Workspace: Left Sidebar + Central Canvas + Right Inspector
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Left Sidebar (Asset & Palette Browser)
                ConfiguratorSidebar(
                    model = model,
                    onAddTemplatePrompt = { factoryType, descriptor ->
                        val selectedPath = when (val sel = model.selection) {
                            is ConfiguratorSelection.DeviceNode -> sel.path
                            is ConfiguratorSelection.Property -> sel.devicePath
                            else -> Name.EMPTY
                        }
                        addTemplateInfo = Triple(selectedPath, factoryType, descriptor)
                    }
                )

                VerticalDivider()

                // Central Scheme Canvas
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ConfiguratorCanvas(
                        model = model,
                        onAddPropertyPrompt = { path -> addPropertyDevicePath = path },
                        onAddSubDevicePrompt = { path -> addDeviceParentPath = path }
                    )
                }

                VerticalDivider()

                // Right Inspector Panel
                ConfiguratorInspector(
                    model = model,
                    onAddPropertyPrompt = { path -> addPropertyDevicePath = path },
                    onAddTemplatePrompt = { path ->
                        addTemplateInfo = Triple(path, "controls.utilities.alarm", null)
                    },
                    onAddSubDevicePrompt = { path -> addDeviceParentPath = path }
                )
            }

            HorizontalDivider()

            // 3. Bottom Panel: Diagnostics & JSON Preview
            ConfiguratorBottomPanel(
                model = model,
                isExpanded = bottomPanelExpanded,
                onToggleExpand = { bottomPanelExpanded = !bottomPanelExpanded }
            )
        }
    }

    // Modal Dialogs
    if (showImportTagTableDialog) {
        ImportTagTableDialog(
            onDismiss = { showImportTagTableDialog = false },
            onImport = { json -> model.importTagTableJson(json) },
            onImportFile = { path -> model.importTagTableFromFile(path) }
        )
    }

    if (showOpenSchemeDialog) {
        OpenSchemeDialog(
            onDismiss = { showOpenSchemeDialog = false },
            onImport = { json -> model.importSchemeJson(json) },
            onImportFile = { path -> model.importSchemeFromFile(path) }
        )
    }

    if (showExportSchemeDialog) {
        ExportSchemeDialog(
            jsonText = exportJsonContent,
            onDismiss = { showExportSchemeDialog = false },
            onSaveToFile = { path -> model.exportSchemeToFile(path) }
        )
    }

    if (addDeviceParentPath != null) {
        val parent = addDeviceParentPath!!
        AddDeviceBlockDialog(
            parentPath = parent,
            onDismiss = { addDeviceParentPath = null },
            onAdd = { name -> model.addDeviceBlock(parent, name) }
        )
    }

    if (addPropertyDevicePath != null) {
        val targetPath = addPropertyDevicePath!!
        AddPropertyDialog(
            devicePath = targetPath,
            onDismiss = { addPropertyDevicePath = null },
            onAdd = { name, type -> model.addProperty(targetPath, name, type) }
        )
    }

    if (addTemplateInfo != null) {
        val (parent, factoryType, descriptor) = addTemplateInfo!!
        AddTemplateDeviceDialog(
            parentPath = parent,
            factoryType = factoryType,
            descriptor = descriptor,
            onDismiss = { addTemplateInfo = null },
            onAdd = { name, params -> model.addTemplateDevice(parent, name, factoryType, params) }
        )
    }
}
