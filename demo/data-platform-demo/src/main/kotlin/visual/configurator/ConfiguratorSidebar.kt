/*
 * LLM generated code: Left Sidebar (Asset & Palette Browser) for Device Scheme Visual Configurator.
 */
package space.kscience.controls.demo.visual.configurator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.tagtable.InternalTagTableColumn
import space.kscience.controls.tagtable.OpcTagTableColumn
import space.kscience.controls.tagtable.PlcTagTableColumn
import space.kscience.controls.tagtable.TagTableColumn
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName

enum class SidebarTab {
    TAG_EXPLORER,
    DEVICE_FACTORIES,
    VALUE_STATE_FACTORIES
}

/**
 * Left sidebar palette containing TagTable assets, Device Factory templates, and ValueState factories.
 */
@Composable
fun ConfiguratorSidebar(
    model: DeviceConfiguratorModel,
    onAddTemplatePrompt: (factoryType: String, descriptor: MetaDescriptor?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(SidebarTab.TAG_EXPLORER) }
    var searchQuery by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.width(320.dp).fillMaxHeight(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sidebar Tabs
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Tab(
                    selected = selectedTab == SidebarTab.TAG_EXPLORER,
                    onClick = { selectedTab = SidebarTab.TAG_EXPLORER },
                    text = { Text("Tags", style = MaterialTheme.typography.labelMedium) },
                    icon = { Icon(Icons.Default.TableView, contentDescription = "Tags", modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == SidebarTab.DEVICE_FACTORIES,
                    onClick = { selectedTab = SidebarTab.DEVICE_FACTORIES },
                    text = { Text("Devices", style = MaterialTheme.typography.labelMedium) },
                    icon = { Icon(Icons.Default.Memory, contentDescription = "Devices", modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == SidebarTab.VALUE_STATE_FACTORIES,
                    onClick = { selectedTab = SidebarTab.VALUE_STATE_FACTORIES },
                    text = { Text("States", style = MaterialTheme.typography.labelMedium) },
                    icon = { Icon(Icons.Default.Functions, contentDescription = "States", modifier = Modifier.size(18.dp)) }
                )
            }

            // Search filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )

            // Tab Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
                when (selectedTab) {
                    SidebarTab.TAG_EXPLORER -> TagExplorerTab(
                        model = model,
                        searchQuery = searchQuery
                    )
                    SidebarTab.DEVICE_FACTORIES -> DeviceFactoryLibraryTab(
                        model = model,
                        searchQuery = searchQuery,
                        onAddTemplatePrompt = onAddTemplatePrompt
                    )
                    SidebarTab.VALUE_STATE_FACTORIES -> ValueStateFactoryLibraryTab(
                        model = model,
                        searchQuery = searchQuery
                    )
                }
            }
        }
    }
}

/**
 * TagTable assets explorer tab.
 */
@Composable
private fun TagExplorerTab(
    model: DeviceConfiguratorModel,
    searchQuery: String
) {
    val tagTable = model.tagTableConfiguration

    if (tagTable == null || tagTable.properties.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No TagTable loaded.\nClick 'TagTable' in toolbar to import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    val filteredTags = remember(tagTable, searchQuery) {
        tagTable.properties.entries.filter { (name, col) ->
            searchQuery.isBlank() ||
                    name.contains(searchQuery, ignoreCase = true) ||
                    col.source.contains(searchQuery, ignoreCase = true) ||
                    (col is OpcTagTableColumn && col.nodeId.contains(searchQuery, ignoreCase = true)) ||
                    (col is PlcTagTableColumn && col.address.contains(searchQuery, ignoreCase = true))
        }.groupBy { it.value.source }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        filteredTags.forEach { (source, tags) ->
            item(key = "source_$source") {
                Text(
                    text = "Source: $source (${tags.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(tags, key = { it.key }) { (tagKey, col) ->
                TagItemCard(
                    tagName = tagKey,
                    column = col,
                    onAddToSelected = {
                        val selectedPath = when (val sel = model.selection) {
                            is ConfiguratorSelection.DeviceNode -> sel.path
                            is ConfiguratorSelection.Property -> sel.devicePath
                            else -> Name.EMPTY
                        }
                        val propName = tagKey.parseAsName().tokens.lastOrNull()?.toString() ?: tagKey
                        model.addProperty(
                            devicePath = selectedPath,
                            propertyName = propName,
                            factoryType = "tagTable",
                            parameters = Meta {
                                "tag" put tagKey
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TagItemCard(
    tagName: String,
    column: TagTableColumn,
    onAddToSelected: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tagName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val detail = when (column) {
                    is OpcTagTableColumn -> "OPC: ${column.nodeId}"
                    is PlcTagTableColumn -> "PLC: ${column.address} (${column.plcValueType})"
                    is InternalTagTableColumn -> "Internal: ${column.deviceName}.${column.propertyName}"
                    else -> "Source: ${column.source}"
                }
                Text(
                    text = "$detail | timer: ${column.timer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(
                onClick = onAddToSelected,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = "Add to device",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Device factory catalog tab.
 */
@Composable
private fun DeviceFactoryLibraryTab(
    model: DeviceConfiguratorModel,
    searchQuery: String,
    onAddTemplatePrompt: (factoryType: String, descriptor: MetaDescriptor?) -> Unit
) {
    val factories = remember(model.context) {
        val discovered = model.availableDeviceFactories
        val defaults = mapOf(
            Name.of("controls.utilities.alarm") to "Alarm threshold monitoring component",
            Name.of("controls.utilities.accumulator") to "Value time accumulator / integrator",
            Name.of("tagTable") to "TagTable virtual device adapter"
        )
        discovered.ifEmpty {
            defaults.mapValues { (k, _) ->
                DeviceFactory(descriptor = null) { _, _ -> error("Template factory stub") }
            }
        }
    }

    val filtered = remember(factories, searchQuery) {
        factories.entries.filter { (name, _) ->
            searchQuery.isBlank() || name.toString().contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(filtered, key = { it.key.toString() }) { (name, factory) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = { onAddTemplatePrompt(name.toString(), factory.descriptor) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Add", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    val desc = factory.descriptor?.description ?: when {
                        name.toString().contains("alarm") -> "Alarm threshold state monitor"
                        name.toString().contains("accumulator") -> "Time window integrator"
                        else -> "Virtual device factory"
                    }
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Value state factory catalog tab.
 */
@Composable
private fun ValueStateFactoryLibraryTab(
    model: DeviceConfiguratorModel,
    searchQuery: String
) {
    val stateTypes = remember {
        listOf(
            "tagTable" to "Reads observable state from physical tag in TagTable",
            "expression" to "Evaluates math expressions over other states (e.g., sum, avg)",
            "deviceProperty" to "Binds to a property of an existing device",
            "timer" to "Generates continuous time tick state"
        )
    }

    val filtered = remember(searchQuery) {
        stateTypes.filter { (type, desc) ->
            searchQuery.isBlank() || type.contains(searchQuery, ignoreCase = true) || desc.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(filtered, key = { it.first }) { (type, desc) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = type,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        FilledTonalButton(
                            onClick = {
                                val selectedPath = when (val sel = model.selection) {
                                    is ConfiguratorSelection.DeviceNode -> sel.path
                                    is ConfiguratorSelection.Property -> sel.devicePath
                                    else -> Name.EMPTY
                                }
                                val propName = "prop_${System.currentTimeMillis() % 1000}"
                                model.addProperty(
                                    devicePath = selectedPath,
                                    propertyName = propName,
                                    factoryType = type
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Insert", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
