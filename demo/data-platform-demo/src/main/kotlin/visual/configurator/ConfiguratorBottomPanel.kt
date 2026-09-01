/*
 * LLM generated code: Bottom Panel (Diagnostics & JSON Preview) for Device Scheme Visual Configurator.
 */
package space.kscience.controls.demo.visual.configurator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BottomPanelTab {
    DIAGNOSTICS,
    JSON_PREVIEW
}

/**
 * Bottom diagnostics and live JSON preview panel.
 */
@Composable
fun ConfiguratorBottomPanel(
    model: DeviceConfiguratorModel,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(BottomPanelTab.DIAGNOSTICS) }
    val diagnostics = remember(model.rootConfiguration, model.tagTableConfiguration) {
        model.validate()
    }
    val clipboardManager = LocalClipboardManager.current
    var copyNotice by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Diagnostics (${diagnostics.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (activeTab == BottomPanelTab.DIAGNOSTICS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            activeTab = BottomPanelTab.DIAGNOSTICS
                            if (!isExpanded) onToggleExpand()
                        }
                    )
                    Text(
                        text = "JSON Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (activeTab == BottomPanelTab.JSON_PREVIEW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            activeTab = BottomPanelTab.JSON_PREVIEW
                            if (!isExpanded) onToggleExpand()
                        }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activeTab == BottomPanelTab.JSON_PREVIEW && isExpanded) {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(model.exportSchemeJson()))
                                copyNotice = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (copyNotice) "Copied!" else "Copy JSON", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Expanded body
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(8.dp)
                ) {
                    when (activeTab) {
                        BottomPanelTab.DIAGNOSTICS -> DiagnosticsList(
                            diagnostics = diagnostics,
                            onFocusTarget = { target ->
                                model.select(target)
                            }
                        )
                        BottomPanelTab.JSON_PREVIEW -> {
                            val jsonText = remember(model.rootConfiguration) {
                                model.exportSchemeJson()
                            }
                            SelectionContainer {
                                Text(
                                    text = jsonText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diagnostics items list view.
 */
@Composable
private fun DiagnosticsList(
    diagnostics: List<ConfiguratorDiagnostic>,
    onFocusTarget: (ConfiguratorSelection) -> Unit
) {
    if (diagnostics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("No validation errors or warnings found in configuration.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(diagnostics) { diag ->
            val icon = when (diag.severity) {
                DiagnosticSeverity.ERROR -> Icons.Default.Error
                DiagnosticSeverity.WARNING -> Icons.Default.Warning
                DiagnosticSeverity.INFO -> Icons.Default.Info
            }
            val tint = when (diag.severity) {
                DiagnosticSeverity.ERROR -> MaterialTheme.colorScheme.error
                DiagnosticSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                DiagnosticSeverity.INFO -> MaterialTheme.colorScheme.primary
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().clickable {
                    if (diag.target != ConfiguratorSelection.None) {
                        onFocusTarget(diag.target)
                    }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = diag.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (diag.target != ConfiguratorSelection.None) {
                        FilledTonalButton(
                            onClick = { onFocusTarget(diag.target) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Focus", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
