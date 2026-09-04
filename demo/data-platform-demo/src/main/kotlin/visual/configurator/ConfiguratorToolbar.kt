/*
 * LLM generated code: Top Navigation Toolbar for Device Scheme Visual Configurator.
 */
package space.kscience.controls.demo.visual.configurator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Top navigation toolbar for the device configurator.
 */
@Composable
fun ConfiguratorToolbar(
    model: DeviceConfiguratorModel,
    onImportTagTableClick: () -> Unit,
    onOpenConfigClick: () -> Unit,
    onExportConfigClick: () -> Unit,
    onToggleDiagnosticsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val diagnostics = remember(model.rootConfiguration, model.tagTableConfiguration) {
        model.validate()
    }
    val errorCount = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warningCount = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main File Actions
            FilledTonalButton(
                onClick = onImportTagTableClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TableView,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("TagTable", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = onOpenConfigClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open", style = MaterialTheme.typography.labelMedium)
            }

            Button(
                onClick = onExportConfigClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export JSON", style = MaterialTheme.typography.labelMedium)
            }

            VerticalDivider(modifier = Modifier.height(28.dp).padding(horizontal = 4.dp))

            // Undo / Redo
            IconButton(
                onClick = { model.undo() },
                enabled = model.canUndo,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { model.redo() },
                enabled = model.canRedo,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    modifier = Modifier.size(18.dp)
                )
            }

            VerticalDivider(modifier = Modifier.height(28.dp).padding(horizontal = 4.dp))

            // Canvas Layout & Zoom Controls
            IconButton(
                onClick = { model.showHierarchyConnections = !model.showHierarchyConnections },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = "Toggle Device Hierarchy Connections",
                    tint = if (model.showHierarchyConnections) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { model.computeAutoLayout() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Auto-Layout Graph",
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { model.zoomFactor = (model.zoomFactor * 1.15f).coerceAtMost(3.0f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom In",
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = "${(model.zoomFactor * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable {
                    model.zoomFactor = 1.0f
                    model.panOffset = androidx.compose.ui.geometry.Offset(50f, 50f)
                }
            )

            IconButton(
                onClick = { model.zoomFactor = (model.zoomFactor / 1.15f).coerceAtLeast(0.3f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Zoom Out",
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = {
                    model.zoomFactor = 1.0f
                    model.panOffset = androidx.compose.ui.geometry.Offset(50f, 50f)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Reset Zoom / Zoom to Fit",
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1.0f))

            // Validation status badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when {
                    errorCount > 0 -> MaterialTheme.colorScheme.errorContainer
                    warningCount > 0 -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.clickable(onClick = onToggleDiagnosticsClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = when {
                            errorCount > 0 -> Icons.Default.ErrorOutline
                            warningCount > 0 -> Icons.Default.WarningAmber
                            else -> Icons.Default.CheckCircleOutline
                        },
                        contentDescription = "Validation status",
                        tint = when {
                            errorCount > 0 -> MaterialTheme.colorScheme.error
                            warningCount > 0 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when {
                            errorCount > 0 -> "$errorCount errors"
                            warningCount > 0 -> "$warningCount warnings"
                            else -> "Valid"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            errorCount > 0 -> MaterialTheme.colorScheme.onErrorContainer
                            warningCount > 0 -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }

            VerticalDivider(modifier = Modifier.height(28.dp).padding(horizontal = 4.dp))

            // Mode Toggle: Edit / Preview
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = model.mode == ConfiguratorMode.EDIT,
                    onClick = { model.mode = ConfiguratorMode.EDIT },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Edit", style = MaterialTheme.typography.labelSmall)
                }
                SegmentedButton(
                    selected = model.mode == ConfiguratorMode.PREVIEW,
                    onClick = { model.mode = ConfiguratorMode.PREVIEW },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Preview", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
