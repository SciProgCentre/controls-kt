/*
 * LLM generated code: Central Canvas workspace for rendering and wiring device nodes in Device Scheme Visual Configurator.
 */
package space.kscience.controls.demo.visual.configurator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import space.kscience.controls.constructor.BoundStateHolder.Companion.DEFAULT_INPUT_NAME
import space.kscience.controls.constructor.ConstructorBinding
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName
import space.kscience.dataforge.names.plus
import kotlin.math.*

/**
 * Interactive infinite canvas rendering nodes and binding wires.
 */
@Composable
fun ConfiguratorCanvas(
    model: DeviceConfiguratorModel,
    onAddPropertyPrompt: (devicePath: Name) -> Unit,
    onAddSubDevicePrompt: (parentPath: Name) -> Unit,
    modifier: Modifier = Modifier
) {
    val nodes = remember(model.rootConfiguration, model.nodePositions.toMap()) {
        model.getCanvasNodes()
    }
    val wires = remember(model.rootConfiguration) {
        model.getCanvasWires()
    }
    val hierarchyEdges = remember(model.rootConfiguration) {
        model.getCanvasHierarchyEdges()
    }

    // Map of port world positions: PortKey -> Offset (in world canvas space)
    val portPositions = remember { mutableStateMapOf<String, Offset>() }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        // 1. Background layer: captures canvas Pan & Zoom and renders Grid & Connection Wires
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        model.panOffset += pan
                        model.zoomFactor = (model.zoomFactor * zoom).coerceIn(0.3f, 3.0f)
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    model.clearSelection()
                    model.connectingFrom = null
                    model.connectingCursorPosition = null
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val zoom = model.zoomFactor
                val pan = model.panOffset

                // Grid background
                drawGrid(pan, zoom, outlineColor)

                // Visual hierarchy connections between parent and child device nodes
                if (model.showHierarchyConnections) {
                    hierarchyEdges.forEach { edge ->
                        val parentPos = model.nodePositions[edge.parentNodeId]
                            ?: nodes.firstOrNull { it.id == edge.parentNodeId }?.position
                            ?: Offset.Zero
                        val parentSize = model.nodeSizes[edge.parentNodeId] ?: IntSize(240, 140)

                        val childPos = model.nodePositions[edge.childNodeId]
                            ?: nodes.firstOrNull { it.id == edge.childNodeId }?.position
                            ?: Offset.Zero
                        val childSize = model.nodeSizes[edge.childNodeId] ?: IntSize(240, 140)

                        val isEdgeHighlighted = when (val sel = model.selection) {
                            is ConfiguratorSelection.DeviceNode -> sel.path == edge.parentPath || sel.path == edge.childPath
                            is ConfiguratorSelection.TemplateNode -> (sel.parentPath == edge.parentPath && sel.name == edge.childName) || sel.parentPath == edge.childPath
                            else -> false
                        }

                        val p1World = calculateHierarchyAnchor(parentPos, parentSize, childPos, childSize, isSource = true)
                        val p2World = calculateHierarchyAnchor(parentPos, parentSize, childPos, childSize, isSource = false)

                        val p1 = p1World * zoom + pan
                        val p2 = p2World * zoom + pan

                        drawHierarchyConnection(
                            p1 = p1,
                            p2 = p2,
                            isHighlighted = isEdgeHighlighted,
                            isTemplate = edge.isTemplate,
                            secondaryColor = secondaryColor,
                            tertiaryColor = tertiaryColor,
                            zoom = zoom
                        )
                    }
                }

                // Existing wires
                wires.forEach { wire ->
                    val sourcePortKey = "out:${wire.sourceDeviceId}:${wire.sourceProperty}"
                    val targetPortKey = "in:${wire.targetDeviceId}:${wire.targetInput}"

                    val sourcePos = portPositions[sourcePortKey]
                    val targetPos = portPositions[targetPortKey]

                    val isWireSelected = model.selection is ConfiguratorSelection.Wire &&
                            (model.selection as ConfiguratorSelection.Wire).sourceDevice == wire.sourceDeviceName &&
                            (model.selection as ConfiguratorSelection.Wire).sourceProperty == wire.sourceProperty &&
                            (model.selection as ConfiguratorSelection.Wire).targetDevice == wire.targetDeviceName &&
                            (model.selection as ConfiguratorSelection.Wire).targetInput == wire.targetInput

                    if (sourcePos != null && targetPos != null) {
                        val p1 = sourcePos * zoom + pan
                        val p2 = targetPos * zoom + pan

                        drawBindingWire(
                            p1 = p1,
                            p2 = p2,
                            isSelected = isWireSelected,
                            isValid = wire.isValid,
                            primaryColor = primaryColor,
                            errorColor = errorColor
                        )
                    }
                }

                // In-progress wire drag visualization
                val conn = model.connectingFrom
                val cursor = model.connectingCursorPosition
                if (conn != null && cursor != null) {
                    val sourceNodeId = if (conn.first == Name.EMPTY) "root" else conn.first.toString()
                    val sourcePortKey = "out:${sourceNodeId}:${conn.second}"
                    val sourcePos = portPositions[sourcePortKey]
                    if (sourcePos != null) {
                        val p1 = sourcePos * zoom + pan
                        val p2 = cursor * zoom + pan
                        drawBindingWire(
                            p1 = p1,
                            p2 = p2,
                            isSelected = true,
                            isValid = true,
                            primaryColor = primaryColor,
                            errorColor = errorColor,
                            isDraft = true
                        )
                    }
                }
            }
        }

        // 2. Interactive Nodes layer
        nodes.forEach { node ->
            val nodePos = node.position
            val screenPos = nodePos * model.zoomFactor + model.panOffset

            CanvasNodeView(
                node = node,
                screenPos = screenPos,
                zoom = model.zoomFactor,
                isConnectingActive = model.connectingFrom != null,
                isSelected = isNodeSelected(node, model.selection),
                onSelect = {
                    when (val type = node.type) {
                        is CanvasNodeType.DeviceBlock -> model.select(ConfiguratorSelection.DeviceNode(type.path))
                        is CanvasNodeType.TemplateDevice -> model.select(ConfiguratorSelection.TemplateNode(type.parentPath, type.name))
                    }
                },
                onDragDelta = { delta ->
                    val currentPos = model.nodePositions[node.id] ?: node.position
                    val newPos = currentPos + (delta / model.zoomFactor)
                    model.updateNodePosition(node.id, newPos)
                },
                onDragEnd = {
                    val finalPos = model.nodePositions[node.id] ?: node.position
                    val snapped = Offset(
                        (finalPos.x / 10f).roundToInt() * 10f,
                        (finalPos.y / 10f).roundToInt() * 10f
                    )
                    model.updateNodePosition(node.id, snapped)
                },
                onPortPositionChange = { portKey, localWorldOffset ->
                    portPositions[portKey] = nodePos + localWorldOffset
                },
                onStartConnecting = { propName ->
                    val devicePath = when (val t = node.type) {
                        is CanvasNodeType.DeviceBlock -> t.path
                        is CanvasNodeType.TemplateDevice -> t.parentPath + t.name.parseAsName()
                    }
                    val sourcePortKey = "out:${node.id}:$propName"
                    val startWorldPos = portPositions[sourcePortKey] ?: node.position
                    model.connectingFrom = devicePath to propName
                    model.connectingCursorPosition = startWorldPos
                },
                onDragConnecting = { dragDelta ->
                    val cur = model.connectingCursorPosition ?: Offset.Zero
                    model.connectingCursorPosition = cur + (dragDelta / model.zoomFactor)
                },
                onEndConnecting = {
                    val cursor = model.connectingCursorPosition
                    val source = model.connectingFrom
                    if (cursor != null && source != null) {
                        // Find closest input port within target radius (35 world pixels)
                        val candidate = portPositions.entries
                            .filter { it.key.startsWith("in:") }
                            .minByOrNull { (it.value - cursor).getDistance() }

                        if (candidate != null && (candidate.value - cursor).getDistance() < 35f) {
                            val parts = candidate.key.removePrefix("in:").split(":")
                            val targetNodeId = parts.firstOrNull() ?: ""
                            val inputName = parts.getOrNull(1) ?: DEFAULT_INPUT_NAME
                            val targetNode = nodes.firstOrNull { it.id == targetNodeId }
                            if (targetNode != null) {
                                val targetDevice = when (val t = targetNode.type) {
                                    is CanvasNodeType.TemplateDevice -> Name.of(t.name)
                                    is CanvasNodeType.DeviceBlock -> if (t.path == Name.EMPTY) Name.EMPTY else t.path
                                }
                                model.addBinding(
                                    ConstructorBinding(
                                        sourceDevice = source.first,
                                        sourceProperty = source.second,
                                        targetDevice = targetDevice,
                                        targetInput = inputName
                                    )
                                )
                            }
                        }
                    }
                    model.connectingFrom = null
                    model.connectingCursorPosition = null
                },
                onConnectTarget = { inputName ->
                    val source = model.connectingFrom
                    if (source != null) {
                        val targetDevice = when (val t = node.type) {
                            is CanvasNodeType.TemplateDevice -> Name.of(t.name)
                            is CanvasNodeType.DeviceBlock -> if (t.path == Name.EMPTY) Name.EMPTY else t.path
                        }
                        val binding = ConstructorBinding(
                            sourceDevice = source.first,
                            sourceProperty = source.second,
                            targetDevice = targetDevice,
                            targetInput = inputName
                        )
                        model.addBinding(binding)
                        model.connectingFrom = null
                        model.connectingCursorPosition = null
                    }
                },
                onAddProperty = { onAddPropertyPrompt((node.type as CanvasNodeType.DeviceBlock).path) },
                onAddSubDevice = { onAddSubDevicePrompt((node.type as CanvasNodeType.DeviceBlock).path) },
                onDelete = {
                    when (val type = node.type) {
                        is CanvasNodeType.DeviceBlock -> if (!type.isRoot) model.removeDeviceBlock(type.path)
                        is CanvasNodeType.TemplateDevice -> model.removeTemplateDevice(type.parentPath, type.name)
                    }
                },
                onSelectProperty = { propName ->
                    val path = (node.type as CanvasNodeType.DeviceBlock).path
                    model.select(ConfiguratorSelection.Property(path, propName))
                },
                onNodeSizeChange = { size ->
                    model.updateNodeSize(node.id, size)
                }
            )
        }
    }
}

private fun Offset.getDistance(): Float {
    return sqrt(x * x + y * y)
}

private fun isNodeSelected(node: CanvasNode, selection: ConfiguratorSelection): Boolean {
    return when (val type = node.type) {
        is CanvasNodeType.DeviceBlock -> {
            when (selection) {
                is ConfiguratorSelection.DeviceNode -> selection.path == type.path
                is ConfiguratorSelection.Property -> selection.devicePath == type.path
                else -> false
            }
        }
        is CanvasNodeType.TemplateDevice -> {
            when (selection) {
                is ConfiguratorSelection.TemplateNode -> selection.parentPath == type.parentPath && selection.name == type.name
                else -> false
            }
        }
    }
}

@Composable
private fun CanvasNodeView(
    node: CanvasNode,
    screenPos: Offset,
    zoom: Float,
    isConnectingActive: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onPortPositionChange: (String, Offset) -> Unit,
    onStartConnecting: (String) -> Unit,
    onDragConnecting: (Offset) -> Unit,
    onEndConnecting: () -> Unit,
    onConnectTarget: (String) -> Unit,
    onAddProperty: () -> Unit,
    onAddSubDevice: () -> Unit,
    onDelete: () -> Unit,
    onSelectProperty: (String) -> Unit,
    onNodeSizeChange: (IntSize) -> Unit
) {
    val isTemplate = node.isTemplate
    var nodeCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isTemplate -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(screenPos.x.roundToInt(), screenPos.y.roundToInt()) }
            .width((node.size.width * zoom).dp.coerceAtLeast(180.dp))
            .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isTemplate) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .onGloballyPositioned { coords ->
                nodeCoords = coords
                val sz = coords.size
                onNodeSizeChange(IntSize((sz.width / zoom).roundToInt(), (sz.height / zoom).roundToInt()))
            }
            .clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar (Draggable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isTemplate) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    )
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (isTemplate) Icons.Default.Memory else Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = if (isTemplate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = node.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isTemplate) {
                        IconButton(onClick = onAddProperty, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Add Property", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onAddSubDevice, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "Add Sub-Device", modifier = Modifier.size(16.dp))
                        }
                    }
                    val isRoot = (node.type as? CanvasNodeType.DeviceBlock)?.isRoot == true
                    if (!isRoot) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete Node",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Node Body
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isTemplate) {
                    Text(
                        text = "Factory: ${node.factoryType ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    node.inputPorts.forEach { inputName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConnectTarget(inputName) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(
                                        if (isConnectingActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                                    .onGloballyPositioned { portCoords ->
                                        val nc = nodeCoords
                                        if (nc != null && nc.isAttached && portCoords.isAttached) {
                                            val local = nc.localPositionOf(portCoords, Offset(7f, 7f))
                                            onPortPositionChange("in:${node.id}:$inputName", local / zoom)
                                        }
                                    }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "In: ${if (inputName.isEmpty()) "default" else inputName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    if (node.properties.isEmpty()) {
                        Text(
                            text = "No properties defined",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    node.properties.forEach { (propName, propConfig) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProperty(propName) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$propName (${propConfig.type})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )

                            // Output Port Dot: supports drag-and-drop to wire as well as click-to-connect
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(MaterialTheme.colorScheme.secondary, CircleShape)
                                    .onGloballyPositioned { portCoords ->
                                        val nc = nodeCoords
                                        if (nc != null && nc.isAttached && portCoords.isAttached) {
                                            val local = nc.localPositionOf(portCoords, Offset(7f, 7f))
                                            onPortPositionChange("out:${node.id}:$propName", local / zoom)
                                        }
                                    }
                                    .pointerInput(node.id, propName) {
                                        detectDragGestures(
                                            onDragStart = {
                                                onStartConnecting(propName)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                onDragConnecting(dragAmount)
                                            },
                                            onDragEnd = {
                                                onEndConnecting()
                                            },
                                            onDragCancel = {
                                                onEndConnecting()
                                            }
                                        )
                                    }
                                    .clickable { onStartConnecting(propName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawGrid(pan: Offset, zoom: Float, color: Color) {
    val gridSize = 40f * zoom
    val width = size.width
    val height = size.height

    val startX = (pan.x % gridSize + gridSize) % gridSize
    val startY = (pan.y % gridSize + gridSize) % gridSize

    var x = startX
    while (x < width) {
        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f
        )
        x += gridSize
    }

    var y = startY
    while (y < height) {
        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )
        y += gridSize
    }
}

private fun DrawScope.drawBindingWire(
    p1: Offset,
    p2: Offset,
    isSelected: Boolean,
    isValid: Boolean,
    primaryColor: Color,
    errorColor: Color,
    isDraft: Boolean = false
) {
    val strokeColor = when {
        !isValid -> errorColor
        isSelected -> primaryColor
        else -> primaryColor.copy(alpha = 0.7f)
    }

    val strokeWidth = if (isSelected) 3.5f else 2.0f
    val pathEffect = if (isDraft) PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) else null

    val dx = (p2.x - p1.x).coerceAtLeast(60f) * 0.5f
    val ctrl1 = Offset(p1.x + dx, p1.y)
    val ctrl2 = Offset(p2.x - dx, p2.y)

    val path = Path().apply {
        moveTo(p1.x, p1.y)
        cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, p2.x, p2.y)
    }

    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = pathEffect
        )
    )

    val angle = atan2(p2.y - ctrl2.y, p2.x - ctrl2.x)
    val arrowLength = 12f
    val arrowWidth = 8f

    val arrowP1 = Offset(
        p2.x - arrowLength * cos(angle) + arrowWidth * sin(angle),
        p2.y - arrowLength * sin(angle) - arrowWidth * cos(angle)
    )
    val arrowP2 = Offset(
        p2.x - arrowLength * cos(angle) - arrowWidth * sin(angle),
        p2.y - arrowLength * sin(angle) + arrowWidth * cos(angle)
    )

    val arrowPath = Path().apply {
        moveTo(p2.x, p2.y)
        lineTo(arrowP1.x, arrowP1.y)
        lineTo(arrowP2.x, arrowP2.y)
        close()
    }

    drawPath(
        path = arrowPath,
        color = strokeColor
    )
}

/*
 * LLM generated code: Anchor calculation and visual rendering for parent/child device hierarchy links.
 */
private fun calculateHierarchyAnchor(
    parentPos: Offset,
    parentSize: IntSize,
    childPos: Offset,
    childSize: IntSize,
    isSource: Boolean
): Offset {
    val pLeft = parentPos.x
    val pTop = parentPos.y
    val pRight = parentPos.x + parentSize.width
    val pBottom = parentPos.y + parentSize.height

    val cLeft = childPos.x
    val cTop = childPos.y
    val cRight = childPos.x + childSize.width
    val cBottom = childPos.y + childSize.height

    val pMidY = pTop + min(30f, parentSize.height / 2f)
    val cMidY = cTop + min(30f, childSize.height / 2f)
    val pMidX = pLeft + parentSize.width / 2f
    val cMidX = cLeft + childSize.width / 2f

    return when {
        // Child is predominantly to the right of parent
        cLeft >= pRight - 20f -> {
            if (isSource) Offset(pRight, pMidY) else Offset(cLeft, cMidY)
        }
        // Child is predominantly to the left of parent
        cRight <= pLeft + 20f -> {
            if (isSource) Offset(pLeft, pMidY) else Offset(cRight, cMidY)
        }
        // Child is predominantly below parent
        cTop >= pBottom - 20f -> {
            if (isSource) Offset(pMidX, pBottom) else Offset(cMidX, cTop)
        }
        // Child is predominantly above parent
        cBottom <= pTop + 20f -> {
            if (isSource) Offset(pMidX, pTop) else Offset(cMidX, cBottom)
        }
        // Overlapping / diagonal relative placement
        else -> {
            val dx = cMidX - pMidX
            val dy = cMidY - pMidY
            if (abs(dx) >= abs(dy)) {
                if (dx >= 0) {
                    if (isSource) Offset(pRight, pMidY) else Offset(cLeft, cMidY)
                } else {
                    if (isSource) Offset(pLeft, pMidY) else Offset(cRight, cMidY)
                }
            } else {
                if (dy >= 0) {
                    if (isSource) Offset(pMidX, pBottom) else Offset(cMidX, cTop)
                } else {
                    if (isSource) Offset(pMidX, pTop) else Offset(cMidX, cBottom)
                }
            }
        }
    }
}

private fun DrawScope.drawHierarchyConnection(
    p1: Offset,
    p2: Offset,
    isHighlighted: Boolean,
    isTemplate: Boolean,
    secondaryColor: Color,
    tertiaryColor: Color,
    zoom: Float
) {
    val baseColor = if (isTemplate) tertiaryColor else secondaryColor
    val strokeColor = if (isHighlighted) baseColor else baseColor.copy(alpha = 0.55f)
    val strokeWidth = if (isHighlighted) 2.5f else 1.5f
    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)

    val dx = p2.x - p1.x
    val dy = p2.y - p1.y

    val path = Path().apply {
        moveTo(p1.x, p1.y)
        if (abs(dx) >= abs(dy)) {
            val cx = dx * 0.5f
            cubicTo(p1.x + cx, p1.y, p2.x - cx, p2.y, p2.x, p2.y)
        } else {
            val cy = dy * 0.5f
            cubicTo(p1.x, p1.y + cy, p2.x, p2.y - cy, p2.x, p2.y)
        }
    }

    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = pathEffect
        )
    )

    // Parent anchor circle marker
    drawCircle(
        color = strokeColor,
        radius = (if (isHighlighted) 4.5f else 3.5f) * zoom.coerceIn(0.7f, 1.3f),
        center = p1
    )

    // Child anchor marker
    drawCircle(
        color = strokeColor,
        radius = (if (isHighlighted) 3.5f else 2.8f) * zoom.coerceIn(0.7f, 1.3f),
        center = p2
    )
}
