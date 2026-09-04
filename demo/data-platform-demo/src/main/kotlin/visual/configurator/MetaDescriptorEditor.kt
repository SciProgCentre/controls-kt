/*
 * LLM generated code: Dynamic form editor based on MetaDescriptor and arbitrary Meta values.
 */
package space.kscience.controls.demo.visual.configurator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName

/**
 * An editor component for DataForge [Meta] structures, dynamically rendering inputs
 * based on a [MetaDescriptor] when provided, and falling back to a flexible key-value editor.
 */
@Composable
fun MetaDescriptorEditor(
    descriptor: MetaDescriptor?,
    meta: Meta,
    onMetaChange: (Meta) -> Unit,
    modifier: Modifier = Modifier
) {
    var rawMode by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (descriptor != null) "Structured Configuration" else "Parameters",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Raw Editor",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = rawMode,
                    onCheckedChange = { rawMode = it },
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        if (rawMode) {
            RawMetaEditor(
                meta = meta,
                onMetaChange = onMetaChange
            )
        } else if (descriptor != null && descriptor.children.isNotEmpty()) {
            DescriptorFormEditor(
                descriptor = descriptor,
                meta = meta,
                onMetaChange = onMetaChange
            )
        } else {
            RawMetaEditor(
                meta = meta,
                onMetaChange = onMetaChange
            )
        }
    }
}

/**
 * Editor form rendering fields according to [MetaDescriptor] child definitions.
 */
@Composable
private fun DescriptorFormEditor(
    descriptor: MetaDescriptor,
    meta: Meta,
    onMetaChange: (Meta) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        descriptor.children.forEach { (token, childDescriptor) ->
            val key = token.toString()
            val childMeta = meta[key] ?: Meta.EMPTY
            val currentValue = meta[key]?.value

            val childValueType = childDescriptor.valueTypes?.firstOrNull()
            val description = childDescriptor.description

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (childValueType != null) {
                            Text(
                                text = childValueType.name.lowercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    if (childValueType == ValueType.BOOLEAN) {
                        val boolVal = currentValue?.boolean ?: false
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = boolVal,
                                onCheckedChange = { checked ->
                                    val newMeta = Meta {
                                        meta.items.forEach { (k, v) ->
                                            if (k.toString() != key) set(k.asName(), v)
                                        }
                                        set(key.parseAsName(), checked.asValue())
                                    }
                                    onMetaChange(newMeta)
                                }
                            )
                            Text(text = if (boolVal) "True" else "False", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (childDescriptor.children.isNotEmpty()) {
                        // Nested child descriptor editor
                        DescriptorFormEditor(
                            descriptor = childDescriptor,
                            meta = childMeta,
                            onMetaChange = { updatedChild ->
                                val newMeta = Meta {
                                    meta.items.forEach { (k, v) ->
                                        if (k.toString() != key) set(k.asName(), v)
                                    }
                                    set(key.parseAsName(), updatedChild)
                                }
                                onMetaChange(newMeta)
                            }
                        )
                    } else {
                        // Text / Number input field
                        var textState by remember(currentValue) {
                            mutableStateOf(currentValue?.string ?: "")
                        }
                        OutlinedTextField(
                            value = textState,
                            onValueChange = { newText ->
                                textState = newText
                                val value = when (childValueType) {
                                    ValueType.NUMBER -> newText.toDoubleOrNull()?.asValue() ?: newText.asValue()
                                    ValueType.BOOLEAN -> newText.toBooleanStrictOrNull()?.asValue() ?: newText.asValue()
                                    else -> newText.asValue()
                                }
                                val newMeta = Meta {
                                    meta.items.forEach { (k, v) ->
                                        if (k.toString() != key) set(k.asName(), v)
                                    }
                                    set(key.parseAsName(), value)
                                }
                                onMetaChange(newMeta)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Key-value and nested node editor for generic [Meta].
 */
@Composable
fun RawMetaEditor(
    meta: Meta,
    onMetaChange: (Meta) -> Unit,
    modifier: Modifier = Modifier
) {
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (meta.items.isEmpty()) {
            Text(
                text = "No parameters defined",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        meta.items.forEach { (token, childMeta) ->
            val key = token.toString()
            val valStr = childMeta.value?.string ?: ""

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(0.35f)
                )

                var editVal by remember(valStr) { mutableStateOf(valStr) }
                OutlinedTextField(
                    value = editVal,
                    onValueChange = { updated ->
                        editVal = updated
                        val num = updated.toDoubleOrNull()
                        val v = if (num != null) num.asValue() else updated.asValue()
                        val newMeta = Meta {
                            meta.items.forEach { (k, oldV) ->
                                if (k.toString() != key) set(k.asName(), oldV)
                            }
                            set(key.parseAsName(), v)
                        }
                        onMetaChange(newMeta)
                    },
                    modifier = Modifier.weight(0.55f),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        val newMeta = Meta {
                            meta.items.forEach { (k, oldV) ->
                                if (k.toString() != key) set(k.asName(), oldV)
                            }
                        }
                        onMetaChange(newMeta)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove parameter",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Add new parameter row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it },
                label = { Text("Key") },
                singleLine = true,
                modifier = Modifier.weight(0.4f)
            )
            OutlinedTextField(
                value = newValue,
                onValueChange = { newValue = it },
                label = { Text("Value") },
                singleLine = true,
                modifier = Modifier.weight(0.45f)
            )
            IconButton(
                onClick = {
                    if (newKey.isNotBlank()) {
                        val num = newValue.toDoubleOrNull()
                        val v = if (num != null) num.asValue() else newValue.asValue()
                        val newMeta = Meta {
                            meta.items.forEach { (k, oldV) ->
                                set(k.asName(), oldV)
                            }
                            set(newKey.trim().parseAsName(), v)
                        }
                        onMetaChange(newMeta)
                        newKey = ""
                        newValue = ""
                    }
                },
                enabled = newKey.isNotBlank(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add parameter",
                    tint = if (newKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
