/* LLM generated code: Improved Compose Tree component for device navigation */
package space.kscience.controls.demo.visual

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.compose.asComposeState
import space.kscience.controls.constructor.propertyAsState
import space.kscience.controls.utilities.Alarm
import space.kscience.controls.utilities.AlarmState
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.lastOrNull
import space.kscience.dataforge.names.plus

/**
 * A hierarchical tree view for [DeviceTree] and its properties.
 */
@Composable
fun DeviceTree(
    node: DeviceTree,
    selectedProperties: Set<Pair<Name, String>>,
    onSelectProperty: (deviceName: Name, propertyName: String, selected: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // We use a state map to keep track of which nodes are expanded.
    // By default, the root node is expanded.
    val expandedNodes = remember { mutableStateMapOf<Name, Boolean>(Name.EMPTY to true) }

    LazyColumn(modifier = modifier) {
        renderDeviceNode(
            name = Name.EMPTY,
            node = node,
            expandedNodes = expandedNodes,
            selectedProperties = selectedProperties,
            onSelectProperty = onSelectProperty,
            level = 0
        )
    }
}

private fun LazyListScope.renderDeviceNode(
    name: Name,
    node: DeviceTree,
    expandedNodes: MutableMap<Name, Boolean>,
    selectedProperties: Set<Pair<Name, String>>,
    onSelectProperty: (deviceName: Name, propertyName: String, selected: Boolean) -> Unit,
    level: Int
) {
    val isExpanded = expandedNodes[name] ?: false

    item(name) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (level * 16).dp)
                .clickable(enabled = node.device != null || node.children.isNotEmpty()) {
                    expandedNodes[name] = !isExpanded
                }
                .padding(vertical = 4.dp)
        ) {
            if (node.device != null || node.children.isNotEmpty()) {
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
            Text(
                text = name.lastOrNull()?.toStringUnescaped() ?: "Root",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    if (isExpanded) {
        // Show properties of the device if it exists
        node.device?.let { device ->
            device.propertyDescriptors.sortedBy { it.name }.forEach { descriptor ->
                item(name + descriptor.name) {
                    PropertyNode(
                        device = device,
                        propertyName = descriptor.name,
                        isSelected = selectedProperties.contains(name to descriptor.name),
                        onSelect = { onSelectProperty(name, descriptor.name, it) },
                        level = level + 1
                    )
                }
            }
        }

        // Show children
        node.children.entries.sortedBy { it.key }.forEach { (childName, childNode) ->
            renderDeviceNode(
                name = name + childName,
                node = childNode,
                expandedNodes = expandedNodes,
                selectedProperties = selectedProperties,
                onSelectProperty = onSelectProperty,
                level = level + 1
            )
        }
    }
}

/**
 * A node representing a single property of a device.
 */
@Composable
fun PropertyNode(
    device: Device,
    propertyName: String,
    isSelected: Boolean,
    onSelect: (Boolean) -> Unit,
    level: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp)
            .padding(vertical = 2.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onSelect,
            enabled = device !is Alarm
        )
        Text(
            text = propertyName,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.weight(1.0f))

        if (device is Alarm && propertyName == "state") {
            val value = device.propertyAsState(
                propertyName = propertyName,
                metaConverter = MetaConverter.serializable<AlarmState>(),
                initialValue = AlarmState("", null)
            ).asComposeState()

            Text(
                text = value.value.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            if (isSelected) {
                val value = device.propertyAsState(propertyName, MetaConverter.double, Double.NaN).asComposeState()

                Text(
                    text = " : ${String.format("%.2f", value.value)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
