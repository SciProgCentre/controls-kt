/*
 * LLM generated code: Modal dialogs for import, export, and node creation in Device Scheme Visual Configurator.
 */
package space.kscience.controls.demo.visual.configurator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Native system file open dialog helper for desktop platforms.
 */
fun openSystemFileDialog(title: String, filterExtension: String = "json"): File? {
    return try {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".$filterExtension", ignoreCase = true) }
        dialog.directory = "."
        dialog.isVisible = true
        val file = dialog.file ?: return null
        val dir = dialog.directory ?: ""
        File(dir, file)
    } catch (e: Exception) {
        null
    }
}

/**
 * Native system file save dialog helper for desktop platforms.
 */
fun saveSystemFileDialog(title: String, defaultName: String = "device-config.json"): File? {
    return try {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true
        val file = dialog.file ?: return null
        val dir = dialog.directory ?: ""
        File(dir, file)
    } catch (e: Exception) {
        null
    }
}

/**
 * Dialog for importing TagTable JSON or loading from file.
 */
@Composable
fun ImportTagTableDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onImportFile: ((Path) -> Unit)? = null
) {
    var jsonText by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import TagTable Configuration") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Load from file or paste JSON content below:",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = filePath,
                        onValueChange = {
                            filePath = it
                            errorMessage = null
                        },
                        placeholder = { Text("path/to/platform-config.json") },
                        label = { Text("File Path") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val selected = openSystemFileDialog("Select Tag Table Configuration JSON")
                            if (selected != null) {
                                filePath = selected.absolutePath
                                try {
                                    jsonText = selected.readText()
                                    errorMessage = null
                                } catch (e: Exception) {
                                    errorMessage = "Error reading file: ${e.message}"
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Browse...")
                    }
                    if (filePath.isNotBlank()) {
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val path = Path(filePath.trim())
                                    if (!path.exists()) {
                                        errorMessage = "File does not exist: $filePath"
                                    } else {
                                        jsonText = path.readText()
                                        errorMessage = null
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Failed to load: ${e.message}"
                                }
                            }
                        ) {
                            Text("Load")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        errorMessage = null
                    },
                    placeholder = { Text("{\n  \"sources\": { ... },\n  \"properties\": { ... }\n}") },
                    label = { Text("Configuration JSON") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).height(200.dp)
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        if (filePath.isNotBlank() && onImportFile != null && jsonText.isBlank()) {
                            val path = Path(filePath.trim())
                            onImportFile(path)
                        } else {
                            onImport(jsonText)
                        }
                        onDismiss()
                    } catch (e: Exception) {
                        errorMessage = "Failed to import: ${e.message}"
                    }
                },
                enabled = jsonText.isNotBlank() || (filePath.isNotBlank() && Path(filePath.trim()).exists())
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Dialog for opening / importing a ConstructorDeviceConfiguration scheme JSON or loading from file.
 */
@Composable
fun OpenSchemeDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onImportFile: ((Path) -> Unit)? = null
) {
    var jsonText by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open Device Scheme") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Load from file or paste JSON content below:",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = filePath,
                        onValueChange = {
                            filePath = it
                            errorMessage = null
                        },
                        placeholder = { Text("path/to/device-config.json") },
                        label = { Text("File Path") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val selected = openSystemFileDialog("Select Device Configuration Scheme JSON")
                            if (selected != null) {
                                filePath = selected.absolutePath
                                try {
                                    jsonText = selected.readText()
                                    errorMessage = null
                                } catch (e: Exception) {
                                    errorMessage = "Error reading file: ${e.message}"
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Browse...")
                    }
                    if (filePath.isNotBlank()) {
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val path = Path(filePath.trim())
                                    if (!path.exists()) {
                                        errorMessage = "File does not exist: $filePath"
                                    } else {
                                        jsonText = path.readText()
                                        errorMessage = null
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Failed to load: ${e.message}"
                                }
                            }
                        ) {
                            Text("Load")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        errorMessage = null
                    },
                    placeholder = { Text("{\n  \"properties\": { ... },\n  \"devices\": { ... }\n}") },
                    label = { Text("Configuration Scheme JSON") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).height(200.dp)
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        if (filePath.isNotBlank() && onImportFile != null && jsonText.isBlank()) {
                            val path = Path(filePath.trim())
                            onImportFile(path)
                        } else {
                            onImport(jsonText)
                        }
                        onDismiss()
                    } catch (e: Exception) {
                        errorMessage = "Failed to import: ${e.message}"
                    }
                },
                enabled = jsonText.isNotBlank() || (filePath.isNotBlank() && Path(filePath.trim()).exists())
            ) {
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Dialog for exporting ConstructorDeviceConfiguration scheme JSON or saving to file.
 */
@Composable
fun ExportSchemeDialog(
    jsonText: String,
    onDismiss: () -> Unit,
    onSaveToFile: ((Path) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Scheme JSON") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Generated ConstructorDeviceConfiguration JSON:",
                    style = MaterialTheme.typography.bodySmall
                )
                SelectionContainer {
                    Text(
                        text = jsonText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                if (savedMessage != null) {
                    Text(
                        text = savedMessage!!,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val selected = saveSystemFileDialog("Save Device Configuration Scheme JSON", "device-config.json")
                        if (selected != null) {
                            try {
                                if (onSaveToFile != null) {
                                    onSaveToFile(selected.toPath())
                                } else {
                                    selected.writeText(jsonText)
                                }
                                savedMessage = "Saved to: ${selected.absolutePath}"
                            } catch (e: Exception) {
                                savedMessage = "Error saving: ${e.message}"
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save to File...")
                }

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(jsonText))
                        copied = true
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (copied) "Copied!" else "Copy JSON")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Dialog for creating a new Device Block.
 */
@Composable
fun AddDeviceBlockDialog(
    parentPath: Name,
    onDismiss: () -> Unit,
    onAdd: (name: String) -> Unit
) {
    var deviceName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Device Block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Parent: ${if (parentPath == Name.EMPTY) "Root" else parentPath.toString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = {
                        deviceName = it
                        try {
                            val parsed = it.trim().parseAsName()
                            nameError = if (parsed == Name.EMPTY) "Name is required" else null
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (deviceName.isNotBlank() && nameError == null) {
                        onAdd(deviceName.trim())
                        onDismiss()
                    }
                },
                enabled = deviceName.isNotBlank() && nameError == null
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for creating a new Template Device node.
 */
@Composable
fun AddTemplateDeviceDialog(
    parentPath: Name,
    factoryType: String,
    descriptor: MetaDescriptor?,
    onDismiss: () -> Unit,
    onAdd: (name: String, parameters: Meta) -> Unit
) {
    var templateName by remember { mutableStateOf(factoryType.substringAfterLast('.')) }
    var parameters by remember { mutableStateOf(Meta.EMPTY) }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Template Device") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Factory: $factoryType",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = templateName,
                    onValueChange = {
                        templateName = it
                        try {
                            val parsed = it.trim().parseAsName()
                            nameError = if (parsed == Name.EMPTY) "Name is required" else null
                        } catch (e: Exception) {
                            nameError = "Invalid name: ${e.message}"
                        }
                    },
                    label = { Text("Device Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Initial Parameters:", style = MaterialTheme.typography.labelMedium)
                MetaDescriptorEditor(
                    descriptor = descriptor,
                    meta = parameters,
                    onMetaChange = { parameters = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (templateName.isNotBlank() && nameError == null) {
                        onAdd(templateName.trim(), parameters)
                        onDismiss()
                    }
                },
                enabled = templateName.isNotBlank() && nameError == null
            ) {
                Text("Instantiate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for creating a new Property.
 */
@Composable
fun AddPropertyDialog(
    devicePath: Name,
    onDismiss: () -> Unit,
    onAdd: (name: String, type: String) -> Unit
) {
    var propName by remember { mutableStateOf("") }
    var propType by remember { mutableStateOf("tagTable") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var typeExpanded by remember { mutableStateOf(false) }

    val types = listOf("tagTable", "expression", "deviceProperty", "timer")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Device Property") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Target Device: ${if (devicePath == Name.EMPTY) "Root" else devicePath.toString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                OutlinedTextField(
                    value = propName,
                    onValueChange = {
                        propName = it
                        try {
                            val parsed = it.trim().parseAsName()
                            nameError = if (parsed == Name.EMPTY) "Name is required" else null
                        } catch (e: Exception) {
                            nameError = "Invalid name: ${e.message}"
                        }
                    },
                    label = { Text("Property Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { typeExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Type: $propType")
                    }
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        types.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    propType = t
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (propName.isNotBlank() && nameError == null) {
                        onAdd(propName.trim(), propType)
                        onDismiss()
                    }
                },
                enabled = propName.isNotBlank() && nameError == null
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
