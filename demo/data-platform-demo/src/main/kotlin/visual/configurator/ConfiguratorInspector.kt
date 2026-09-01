/*
 * LLM generated code: Right Inspector Panel (Context-Aware Property Editor) for Device Scheme Visual Configurator.
 */
package space.kscience.controls.demo.visual.configurator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.kscience.controls.constructor.BoundStateHolder.Companion.DEFAULT_INPUT_NAME
import space.kscience.controls.constructor.ConstructorBinding
import space.kscience.controls.constructor.ConstructorDeviceConfiguration
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.cutLast
import space.kscience.dataforge.names.parseAsName
import space.kscience.dataforge.names.plus

/**
 * Context-aware property inspector panel.
 */
@Composable
fun ConfiguratorInspector(
    model: DeviceConfiguratorModel,
    onAddPropertyPrompt: (devicePath: Name) -> Unit,
    onAddTemplatePrompt: (parentPath: Name) -> Unit,
    onAddSubDevicePrompt: (parentPath: Name) -> Unit,
    modifier: Modifier = Modifier
) {
    val selection = model.selection

    Surface(
        modifier = modifier.width(340.dp).fillMaxHeight(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Inspector",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (selection != ConfiguratorSelection.None) {
                    IconButton(onClick = { model.clearSelection() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Deselect", modifier = Modifier.size(16.dp))
                    }
                }
            }

            HorizontalDivider()

            when (selection) {
                is ConfiguratorSelection.None -> RootOverviewInspector(model = model)
                is ConfiguratorSelection.DeviceNode -> DeviceNodeInspector(
                    devicePath = selection.path,
                    model = model,
                    onAddProperty = { onAddPropertyPrompt(selection.path) },
                    onAddTemplate = { onAddTemplatePrompt(selection.path) },
                    onAddSubDevice = { onAddSubDevicePrompt(selection.path) }
                )
                is ConfiguratorSelection.TemplateNode -> TemplateNodeInspector(
                    parentPath = selection.parentPath,
                    name = selection.name,
                    model = model
                )
                is ConfiguratorSelection.Property -> PropertyInspector(
                    devicePath = selection.devicePath,
                    propertyName = selection.propertyName,
                    model = model
                )
                is ConfiguratorSelection.Wire -> WireInspector(
                    wireSelection = selection,
                    model = model
                )
            }
        }
    }
}

/**
 * Inspector when nothing is selected.
 */
@Composable
private fun RootOverviewInspector(model: DeviceConfiguratorModel) {
    val config = model.rootConfiguration
    val nodes = model.getCanvasNodes()
    val wires = model.getCanvasWires()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Scheme Overview", style = MaterialTheme.typography.titleSmall)
        Text(
            "Select an element in the canvas or sidebar to inspect its parameters and settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Total Nodes: ${nodes.size}", style = MaterialTheme.typography.bodyMedium)
                Text("Root Properties: ${config.properties.size}", style = MaterialTheme.typography.bodyMedium)
                Text("Sub-Devices: ${config.devices.size}", style = MaterialTheme.typography.bodyMedium)
                Text("Template Components: ${config.components.size}", style = MaterialTheme.typography.bodyMedium)
                Text("Bindings / Wires: ${wires.size}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Root Parameters", style = MaterialTheme.typography.titleSmall)
        MetaDescriptorEditor(
            descriptor = null,
            meta = config.parameters,
            onMetaChange = { updated ->
                model.updateSubConfiguration(Name.EMPTY) { root ->
                    ConstructorDeviceConfiguration(
                        properties = root.properties,
                        devices = root.devices,
                        components = root.components,
                        bindings = root.bindings,
                        parameters = updated,
                        metadata = root.metadata
                    )
                }
            }
        )
    }
}

/**
 * Inspector for a Device Block node.
 */
@Composable
private fun DeviceNodeInspector(
    devicePath: Name,
    model: DeviceConfiguratorModel,
    onAddProperty: () -> Unit,
    onAddTemplate: () -> Unit,
    onAddSubDevice: () -> Unit
) {
    val config = model.findDeviceConfig(devicePath)
    if (config == null) {
        Text("Device not found at $devicePath", color = MaterialTheme.colorScheme.error)
        return
    }

    val isRootDevice = devicePath == Name.EMPTY
    val deviceName = if (isRootDevice) "root" else devicePath.tokens.lastOrNull()?.toString() ?: ""
    var editName by remember(deviceName) { mutableStateOf(deviceName) }
    var nameError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (isRootDevice) "Root Device Block" else "Device Block: $deviceName",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        if (!isRootDevice) {
            OutlinedTextField(
                value = editName,
                onValueChange = { newName ->
                    editName = newName
                    try {
                        val parsed = newName.trim().parseAsName()
                        nameError = if (parsed == Name.EMPTY) "Name cannot be empty" else null
                    } catch (e: Exception) {
                        nameError = "Invalid name format: ${e.message}"
                    }
                },
                label = { Text("Device Name") },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (editName.trim() != deviceName && nameError == null && editName.isNotBlank()) {
                Button(
                    onClick = {
                        val parentPath = devicePath.cutLast()
                        model.updateSubConfiguration(parentPath) { parent ->
                            val current = parent.devices[deviceName] ?: return@updateSubConfiguration parent
                            val updatedDevices = parent.devices.toMutableMap()
                            updatedDevices.remove(deviceName)
                            updatedDevices[editName.trim()] = current
                            ConstructorDeviceConfiguration(
                                properties = parent.properties,
                                devices = updatedDevices,
                                components = parent.components,
                                bindings = parent.bindings,
                                parameters = parent.parameters,
                                metadata = parent.metadata
                            )
                        }
                        val newPath = parentPath + editName.trim().parseAsName()
                        model.select(ConfiguratorSelection.DeviceNode(newPath))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Rename")
                }
            }
        }

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(
                onClick = onAddProperty,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)
            ) {
                Text("+ Property", style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalButton(
                onClick = onAddTemplate,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)
            ) {
                Text("+ Template", style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalButton(
                onClick = onAddSubDevice,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)
            ) {
                Text("+ Sub-Dev", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Parameters & Metadata
        Text("Device Parameters", style = MaterialTheme.typography.titleSmall)
        MetaDescriptorEditor(
            descriptor = null,
            meta = config.parameters,
            onMetaChange = { updated ->
                model.updateSubConfiguration(devicePath) { target ->
                    ConstructorDeviceConfiguration(
                        properties = target.properties,
                        devices = target.devices,
                        components = target.components,
                        bindings = target.bindings,
                        parameters = updated,
                        metadata = target.metadata
                    )
                }
            }
        )

        if (!isRootDevice) {
            OutlinedButton(
                onClick = { model.removeDeviceBlock(devicePath) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete Device Block")
            }
        }
    }
}

/**
 * Inspector for a Template Device node.
 */
@Composable
private fun TemplateNodeInspector(
    parentPath: Name,
    name: String,
    model: DeviceConfiguratorModel
) {
    val parentConfig = model.findDeviceConfig(parentPath)
    val template = parentConfig?.components?.get(name)

    if (template == null) {
        Text("Template device '$name' not found", color = MaterialTheme.colorScheme.error)
        return
    }

    val factory = model.availableDeviceFactories[name.parseAsName()]
        ?: model.availableDeviceFactories[template.type.parseAsName()]
    val descriptor = factory?.descriptor

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Template Device: $name",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.tertiary
        )

        Text(
            text = "Factory Type: ${template.type}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Parameters Form", style = MaterialTheme.typography.titleSmall)
        MetaDescriptorEditor(
            descriptor = descriptor,
            meta = template.parameters,
            onMetaChange = { updated ->
                model.updateTemplateParameters(parentPath, name, updated)
            }
        )

        OutlinedButton(
            onClick = { model.removeTemplateDevice(parentPath, name) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Delete Template Device")
        }
    }
}

/**
 * Inspector for a Device Property.
 */
@Composable
private fun PropertyInspector(
    devicePath: Name,
    propertyName: String,
    model: DeviceConfiguratorModel
) {
    val config = model.findDeviceConfig(devicePath)
    val prop = config?.properties?.get(propertyName)

    if (prop == null) {
        Text("Property '$propertyName' not found", color = MaterialTheme.colorScheme.error)
        return
    }

    var editName by remember(propertyName) { mutableStateOf(propertyName) }
    var selectedType by remember(prop.type) { mutableStateOf(prop.type) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Property: $propertyName",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        OutlinedTextField(
            value = editName,
            onValueChange = { editName = it },
            label = { Text("Property Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Value State Factory Type selector
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { typeDropdownExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Type: $selectedType")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(
                expanded = typeDropdownExpanded,
                onDismissRequest = { typeDropdownExpanded = false }
            ) {
                model.standardValueStateTypes.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t) },
                        onClick = {
                            selectedType = t
                            typeDropdownExpanded = false
                            model.updateProperty(
                                devicePath = devicePath,
                                oldName = propertyName,
                                newName = editName,
                                type = t,
                                parameters = prop.parameters,
                                metadata = prop.metadata
                            )
                        }
                    )
                }
            }
        }

        // Specific editor for TagTable property
        if (selectedType == "tagTable") {
            val currentTag = prop.parameters["tag"]?.string ?: ""
            var tagInput by remember(currentTag) { mutableStateOf(currentTag) }

            OutlinedTextField(
                value = tagInput,
                onValueChange = {
                    tagInput = it
                    val updatedMeta = Meta {
                        "tag" put it.trim()
                    }
                    model.updateProperty(
                        devicePath = devicePath,
                        oldName = propertyName,
                        newName = editName,
                        type = selectedType,
                        parameters = updatedMeta,
                        metadata = prop.metadata
                    )
                },
                label = { Text("Tag Name (from TagTable)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // General parameters editor
        Text("Parameters", style = MaterialTheme.typography.titleSmall)
        MetaDescriptorEditor(
            descriptor = null,
            meta = prop.parameters,
            onMetaChange = { updated ->
                model.updateProperty(
                    devicePath = devicePath,
                    oldName = propertyName,
                    newName = editName,
                    type = selectedType,
                    parameters = updated,
                    metadata = prop.metadata
                )
            }
        )

        if (editName.trim() != propertyName && editName.isNotBlank()) {
            Button(
                onClick = {
                    model.updateProperty(
                        devicePath = devicePath,
                        oldName = propertyName,
                        newName = editName.trim(),
                        type = selectedType,
                        parameters = prop.parameters,
                        metadata = prop.metadata
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Rename")
            }
        }

        OutlinedButton(
            onClick = { model.removeProperty(devicePath, propertyName) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Delete Property")
        }
    }
}

/**
 * Inspector for a Binding Wire connection.
 */
@Composable
private fun WireInspector(
    wireSelection: ConfiguratorSelection.Wire,
    model: DeviceConfiguratorModel
) {
    val wires = model.getCanvasWires()
    val wire = wires.find {
        it.sourceDeviceName == wireSelection.sourceDevice &&
                it.sourceProperty == wireSelection.sourceProperty &&
                it.targetDeviceName == wireSelection.targetDevice &&
                it.targetInput == wireSelection.targetInput
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Binding Connection",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Source Device: ${if (wireSelection.sourceDevice == Name.EMPTY) "root" else wireSelection.sourceDevice.toString()}")
                Text("Source Property: ${wireSelection.sourceProperty}")
                Text("Target Device: ${wireSelection.targetDevice}")
                Text("Target Input: ${if (wireSelection.targetInput.isEmpty()) DEFAULT_INPUT_NAME else wireSelection.targetInput}")
            }
        }

        if (wire != null && !wire.isValid) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = wire.errorMessage ?: "Invalid wire connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                val binding = ConstructorBinding(
                    sourceDevice = wireSelection.sourceDevice,
                    sourceProperty = wireSelection.sourceProperty,
                    targetDevice = wireSelection.targetDevice,
                    targetInput = wireSelection.targetInput
                )
                model.removeBinding(binding)
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Delete Binding")
        }
    }
}
