/*
 * LLM generated code: State management, graph model, validation, and serialization for the Device Scheme Visual Configurator.
 */
package space.kscience.controls.demo.visual.configurator

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import space.kscience.controls.api.DeviceTreeFactory
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.BoundStateHolder.Companion.DEFAULT_INPUT_NAME
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.tagtable.TagTableConfiguration
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.gather
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.*
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.math.max

private val configuratorJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Type of node displayed on the canvas.
 */
sealed interface CanvasNodeType {
    data class DeviceBlock(val path: Name, val isRoot: Boolean = false) : CanvasNodeType
    data class TemplateDevice(val parentPath: Name, val name: String, val factoryType: String) : CanvasNodeType
}

/**
 * Representation of a parent-child hierarchy link between device nodes.
 */
data class CanvasHierarchyEdge(
    val id: String,
    val parentNodeId: String,
    val parentPath: Name,
    val childNodeId: String,
    val childPath: Name,
    val childName: String,
    val isTemplate: Boolean = false
)

/**
 * Representation of a visual node on the scheme canvas.
 */
data class CanvasNode(
    val id: String,
    val title: String,
    val type: CanvasNodeType,
    val position: Offset,
    val size: IntSize = IntSize(240, 160),
    val properties: Map<String, PropertyConfiguration> = emptyMap(),
    val inputPorts: List<String> = listOf(DEFAULT_INPUT_NAME),
    val outputPorts: List<String> = emptyList(),
    val isTemplate: Boolean = false,
    val factoryType: String? = null,
    val parameters: Meta = Meta.EMPTY,
    val metadata: Meta = Meta.EMPTY
)

/**
 * Representation of a connection wire between ports.
 */
data class CanvasWire(
    val id: String,
    val sourceDeviceId: String,
    val sourceDeviceName: Name,
    val sourceProperty: String,
    val targetDeviceId: String,
    val targetDeviceName: Name,
    val targetInput: String,
    val defaultValue: Meta = Meta.EMPTY,
    val metadata: Meta = Meta.EMPTY,
    val isValid: Boolean = true,
    val errorMessage: String? = null
)

/**
 * Target selection in the visual configurator.
 */
sealed interface ConfiguratorSelection {
    data object None : ConfiguratorSelection
    data class DeviceNode(val path: Name) : ConfiguratorSelection
    data class TemplateNode(val parentPath: Name, val name: String) : ConfiguratorSelection
    data class Property(val devicePath: Name, val propertyName: String) : ConfiguratorSelection
    data class Wire(
        val sourceDevice: Name,
        val sourceProperty: String,
        val targetDevice: Name,
        val targetInput: String
    ) : ConfiguratorSelection
}

/**
 * Diagnostic message severity.
 */
enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

/**
 * Diagnostic validation issue.
 */
data class ConfiguratorDiagnostic(
    val severity: DiagnosticSeverity,
    val message: String,
    val target: ConfiguratorSelection = ConfiguratorSelection.None,
    val details: String? = null
)

/**
 * Editor mode.
 */
enum class ConfiguratorMode {
    EDIT,
    PREVIEW
}

/**
 * State and logic manager for the Device Scheme Configurator.
 */
@Stable
class DeviceConfiguratorModel(
    val context: Context,
    initialConfiguration: ConstructorDeviceConfiguration? = null,
    initialTagTable: TagTableConfiguration? = null
) {
    var rootConfiguration by mutableStateOf(initialConfiguration ?: ConstructorDeviceConfiguration(emptyMap()))
        private set

    var tagTableConfiguration by mutableStateOf<TagTableConfiguration?>(initialTagTable)
        private set

    var currentSchemePath by mutableStateOf<Path?>(null)
    var currentTagTablePath by mutableStateOf<Path?>(null)

    var mode by mutableStateOf(ConfiguratorMode.EDIT)

    var selection by mutableStateOf<ConfiguratorSelection>(ConfiguratorSelection.None)

    var panOffset by mutableStateOf(Offset(50f, 50f))
    var zoomFactor by mutableFloatStateOf(1.0f)

    val nodePositions = mutableStateMapOf<String, Offset>()
    val nodeSizes = mutableStateMapOf<String, IntSize>()

    var showHierarchyConnections by mutableStateOf(true)

    var connectingFrom by mutableStateOf<Pair<Name, String>?>(null)
    var connectingCursorPosition by mutableStateOf<Offset?>(null)

    private val undoStack = mutableListOf<ConstructorDeviceConfiguration>()
    private val redoStack = mutableListOf<ConstructorDeviceConfiguration>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val availableDeviceFactories: Map<Name, DeviceTreeFactory> by lazy {
        try {
            context.gather(DeviceManager.DEVICE_FACTORY_TARGET, DeviceTreeFactory::class)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    val availableValueStateFactories: Map<String, ValueStateFactory> by lazy {
        try {
            context.gather(ValueStateFactory.PROVIDER_TAGET, ValueStateFactory::class)
                .mapKeys { it.key.toString() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    val standardValueStateTypes: List<String> = listOf(
        "tagTable",
        "expression",
        "deviceProperty",
        "timer"
    )

    init {
        computeAutoLayout()
    }

    private fun pushHistory() {
        undoStack.add(rootConfiguration)
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeLast()
            redoStack.add(rootConfiguration)
            rootConfiguration = previous
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeLast()
            undoStack.add(rootConfiguration)
            rootConfiguration = next
        }
    }

    fun setConfiguration(config: ConstructorDeviceConfiguration) {
        pushHistory()
        rootConfiguration = config
        computeAutoLayout()
    }

    fun setTagTable(tagTable: TagTableConfiguration?) {
        tagTableConfiguration = tagTable
    }

    fun select(target: ConfiguratorSelection) {
        selection = target
    }

    fun clearSelection() {
        selection = ConfiguratorSelection.None
    }

    fun updateNodePosition(nodeId: String, offset: Offset) {
        nodePositions[nodeId] = offset
    }

    fun updateNodeSize(nodeId: String, size: IntSize) {
        nodeSizes[nodeId] = size
    }

    fun findDeviceConfig(path: Name): ConstructorDeviceConfiguration? {
        if (path == Name.EMPTY) return rootConfiguration
        var current: ConstructorDeviceConfiguration = rootConfiguration
        for (token in path.tokens) {
            current = current.devices[token.toString()] ?: return null
        }
        return current
    }

    fun updateSubConfiguration(path: Name, transform: (ConstructorDeviceConfiguration) -> ConstructorDeviceConfiguration) {
        pushHistory()
        fun updateRec(remainingPath: Name, currentConfig: ConstructorDeviceConfiguration): ConstructorDeviceConfiguration {
            if (remainingPath == Name.EMPTY) {
                return transform(currentConfig)
            }
            val firstToken = remainingPath.tokens.firstOrNull()?.toString() ?: return currentConfig
            val childConfig = currentConfig.devices[firstToken] ?: return currentConfig
            val updatedChild = updateRec(remainingPath.cutFirst(), childConfig)
            return ConstructorDeviceConfiguration(
                properties = currentConfig.properties,
                devices = currentConfig.devices + (firstToken to updatedChild),
                components = currentConfig.components,
                bindings = currentConfig.bindings,
                parameters = currentConfig.parameters,
                metadata = currentConfig.metadata
            )
        }

        rootConfiguration = updateRec(path, rootConfiguration)
    }

    fun addDeviceBlock(parentPath: Name, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        updateSubConfiguration(parentPath) { parent ->
            val updatedDevices = parent.devices + (trimmed to ConstructorDeviceConfiguration(emptyMap()))
            ConstructorDeviceConfiguration(
                properties = parent.properties,
                devices = updatedDevices,
                components = parent.components,
                bindings = parent.bindings,
                parameters = parent.parameters,
                metadata = parent.metadata
            )
        }
        val newPath = parentPath + trimmed.parseAsName()
        selection = ConfiguratorSelection.DeviceNode(newPath)
        val pos = nodePositions[parentPath.toString()] ?: panOffset
        nodePositions[newPath.toString()] = Offset(pos.x + 280f, pos.y + 40f)
    }

    fun removeDeviceBlock(path: Name) {
        if (path == Name.EMPTY) return
        val parentPath = path.cutLast()
        val deviceName = path.tokens.lastOrNull()?.toString() ?: return
        updateSubConfiguration(parentPath) { parent ->
            val updatedDevices = parent.devices - deviceName
            val updatedBindings = parent.bindings.filterNot {
                it.sourceDevice == path || it.targetDevice == path ||
                it.sourceDevice == Name.of(deviceName) || it.targetDevice == Name.of(deviceName)
            }.toSet()
            ConstructorDeviceConfiguration(
                properties = parent.properties,
                devices = updatedDevices,
                components = parent.components,
                bindings = updatedBindings,
                parameters = parent.parameters,
                metadata = parent.metadata
            )
        }
        nodePositions.remove(path.toString())
        selection = ConfiguratorSelection.None
    }

    fun addTemplateDevice(parentPath: Name, name: String, factoryType: String, parameters: Meta = Meta.EMPTY) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        updateSubConfiguration(parentPath) { parent ->
            val updatedComponents = parent.components + (trimmed to TemplateDeviceConfiguration(
                type = factoryType,
                parameters = parameters
            ))
            ConstructorDeviceConfiguration(
                properties = parent.properties,
                devices = parent.devices,
                components = updatedComponents,
                bindings = parent.bindings,
                parameters = parent.parameters,
                metadata = parent.metadata
            )
        }
        selection = ConfiguratorSelection.TemplateNode(parentPath, trimmed)
        val parentPos = nodePositions[parentPath.toString()] ?: panOffset
        val templateId = if (parentPath == Name.EMPTY) "tmpl:$trimmed" else "$parentPath:tmpl:$trimmed"
        nodePositions[templateId] = Offset(parentPos.x + 300f, parentPos.y + 120f)
    }

    fun removeTemplateDevice(parentPath: Name, name: String) {
        updateSubConfiguration(parentPath) { parent ->
            val updatedComponents = parent.components - name
            val updatedBindings = parent.bindings.filterNot {
                it.targetDevice == Name.of(name) || it.sourceDevice == Name.of(name)
            }.toSet()
            ConstructorDeviceConfiguration(
                properties = parent.properties,
                devices = parent.devices,
                components = updatedComponents,
                bindings = updatedBindings,
                parameters = parent.parameters,
                metadata = parent.metadata
            )
        }
        val templateId = if (parentPath == Name.EMPTY) "tmpl:$name" else "$parentPath:tmpl:$name"
        nodePositions.remove(templateId)
        selection = ConfiguratorSelection.None
    }

    fun addProperty(
        devicePath: Name,
        propertyName: String,
        factoryType: String = "tagTable",
        parameters: Meta = Meta.EMPTY
    ) {
        val trimmed = propertyName.trim()
        if (trimmed.isEmpty()) return
        updateSubConfiguration(devicePath) { target ->
            val updatedProps = target.properties + (trimmed to PropertyConfiguration(
                type = factoryType,
                parameters = parameters
            ))
            ConstructorDeviceConfiguration(
                properties = updatedProps,
                devices = target.devices,
                components = target.components,
                bindings = target.bindings,
                parameters = target.parameters,
                metadata = target.metadata
            )
        }
        selection = ConfiguratorSelection.Property(devicePath, trimmed)
    }

    fun removeProperty(devicePath: Name, propertyName: String) {
        updateSubConfiguration(devicePath) { target ->
            val updatedProps = target.properties - propertyName
            val updatedBindings = target.bindings.filterNot {
                (it.sourceDevice == Name.EMPTY || it.sourceDevice == devicePath) && it.sourceProperty == propertyName
            }.toSet()
            ConstructorDeviceConfiguration(
                properties = updatedProps,
                devices = target.devices,
                components = target.components,
                bindings = updatedBindings,
                parameters = target.parameters,
                metadata = target.metadata
            )
        }
        selection = ConfiguratorSelection.DeviceNode(devicePath)
    }

    fun updateProperty(
        devicePath: Name,
        oldName: String,
        newName: String,
        type: String,
        parameters: Meta,
        metadata: Meta = Meta.EMPTY
    ) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        updateSubConfiguration(devicePath) { target ->
            val mutableProps = target.properties.toMutableMap()
            mutableProps.remove(oldName)
            mutableProps[trimmed] = PropertyConfiguration(
                type = type,
                parameters = parameters,
                metadata = metadata
            )
            val updatedBindings = if (oldName != trimmed) {
                target.bindings.map { binding ->
                    if ((binding.sourceDevice == Name.EMPTY || binding.sourceDevice == devicePath) && binding.sourceProperty == oldName) {
                        ConstructorBinding(
                            sourceDevice = binding.sourceDevice,
                            sourceProperty = trimmed,
                            targetDevice = binding.targetDevice,
                            targetInput = binding.targetInput,
                            defaultValue = binding.defaultValue,
                            metadata = binding.metadata
                        )
                    } else {
                        binding
                    }
                }.toSet()
            } else {
                target.bindings
            }
            ConstructorDeviceConfiguration(
                properties = mutableProps,
                devices = target.devices,
                components = target.components,
                bindings = updatedBindings,
                parameters = target.parameters,
                metadata = target.metadata
            )
        }
        selection = ConfiguratorSelection.Property(devicePath, trimmed)
    }

    fun updateTemplateParameters(parentPath: Name, name: String, parameters: Meta) {
        updateSubConfiguration(parentPath) { parent ->
            val existing = parent.components[name] ?: return@updateSubConfiguration parent
            val updatedComponents = parent.components + (name to TemplateDeviceConfiguration(
                type = existing.type,
                parameters = parameters,
                metadata = existing.metadata
            ))
            ConstructorDeviceConfiguration(
                properties = parent.properties,
                devices = parent.devices,
                components = updatedComponents,
                bindings = parent.bindings,
                parameters = parent.parameters,
                metadata = parent.metadata
            )
        }
    }

    fun addBinding(binding: ConstructorBinding, containerPath: Name = Name.EMPTY) {
        updateSubConfiguration(containerPath) { container ->
            val filtered = container.bindings.filterNot {
                it.sourceDevice == binding.sourceDevice &&
                it.sourceProperty == binding.sourceProperty &&
                it.targetDevice == binding.targetDevice &&
                it.targetInput == binding.targetInput
            }.toSet()
            ConstructorDeviceConfiguration(
                properties = container.properties,
                devices = container.devices,
                components = container.components,
                bindings = filtered + binding,
                parameters = container.parameters,
                metadata = container.metadata
            )
        }
        selection = ConfiguratorSelection.Wire(
            sourceDevice = binding.sourceDevice,
            sourceProperty = binding.sourceProperty,
            targetDevice = binding.targetDevice,
            targetInput = binding.targetInput
        )
    }

    fun removeBinding(binding: ConstructorBinding, containerPath: Name = Name.EMPTY) {
        updateSubConfiguration(containerPath) { container ->
            val updatedBindings = container.bindings.filterNot {
                it.sourceDevice == binding.sourceDevice &&
                it.sourceProperty == binding.sourceProperty &&
                it.targetDevice == binding.targetDevice &&
                it.targetInput == binding.targetInput
            }.toSet()
            ConstructorDeviceConfiguration(
                properties = container.properties,
                devices = container.devices,
                components = container.components,
                bindings = updatedBindings,
                parameters = container.parameters,
                metadata = container.metadata
            )
        }
        selection = ConfiguratorSelection.None
    }

    fun getCanvasNodes(): List<CanvasNode> {
        val result = mutableListOf<CanvasNode>()

        fun visit(path: Name, config: ConstructorDeviceConfiguration, isRoot: Boolean) {
            val nodeId = if (path == Name.EMPTY) "root" else path.toString()
            val pos = nodePositions[nodeId] ?: Offset(100f, 100f)
            val title = if (path == Name.EMPTY) "Root Device" else path.tokens.lastOrNull()?.toString() ?: "Device"

            result.add(
                CanvasNode(
                    id = nodeId,
                    title = title,
                    type = CanvasNodeType.DeviceBlock(path, isRoot),
                    position = pos,
                    properties = config.properties,
                    inputPorts = emptyList(),
                    outputPorts = config.properties.keys.toList(),
                    isTemplate = false,
                    parameters = config.parameters,
                    metadata = config.metadata
                )
            )

            config.components.forEach { (tmplName, tmplConfig) ->
                val tmplId = if (path == Name.EMPTY) "tmpl:$tmplName" else "$path:tmpl:$tmplName"
                val tmplPos = nodePositions[tmplId] ?: Offset(pos.x + 280f, pos.y + 60f)
                result.add(
                    CanvasNode(
                        id = tmplId,
                        title = tmplName,
                        type = CanvasNodeType.TemplateDevice(path, tmplName, tmplConfig.type),
                        position = tmplPos,
                        properties = emptyMap(),
                        inputPorts = listOf(DEFAULT_INPUT_NAME, "value"),
                        outputPorts = listOf("state"),
                        isTemplate = true,
                        factoryType = tmplConfig.type,
                        parameters = tmplConfig.parameters,
                        metadata = tmplConfig.metadata
                    )
                )
            }

            config.devices.forEach { (subName, subConfig) ->
                visit(path + subName.parseAsName(), subConfig, false)
            }
        }

        visit(Name.EMPTY, rootConfiguration, true)
        return result
    }

    fun getCanvasWires(): List<CanvasWire> {
        val wires = mutableListOf<CanvasWire>()

        fun visitBindings(containerPath: Name, config: ConstructorDeviceConfiguration) {
            config.bindings.forEach { binding ->
                val sourcePath = if (binding.sourceDevice == Name.EMPTY) containerPath else containerPath + binding.sourceDevice
                val targetPath = if (binding.targetDevice == Name.EMPTY) containerPath else containerPath + binding.targetDevice

                val sourceNodeId = if (sourcePath == Name.EMPTY) "root" else sourcePath.toString()
                val targetNodeId = if (containerPath == Name.EMPTY) "tmpl:${binding.targetDevice}" else "$containerPath:tmpl:${binding.targetDevice}"

                val targetDeviceName = binding.targetDevice.toString()
                val targetExists = config.components.containsKey(targetDeviceName) ||
                        config.devices.containsKey(targetDeviceName)
                val sourceConfig = findDeviceConfig(sourcePath)
                val sourcePropExists = sourceConfig?.properties?.containsKey(binding.sourceProperty) == true

                val isValid = targetExists && sourcePropExists
                val errorMsg = when {
                    !targetExists -> "Target device '${binding.targetDevice}' not found"
                    !sourcePropExists -> "Source property '${binding.sourceProperty}' not found in $sourcePath"
                    else -> null
                }

                val wireId = "${sourceNodeId}.${binding.sourceProperty}->${targetNodeId}.${binding.targetInput}"

                wires.add(
                    CanvasWire(
                        id = wireId,
                        sourceDeviceId = sourceNodeId,
                        sourceDeviceName = sourcePath,
                        sourceProperty = binding.sourceProperty,
                        targetDeviceId = targetNodeId,
                        targetDeviceName = targetPath,
                        targetInput = binding.targetInput,
                        defaultValue = binding.defaultValue,
                        metadata = binding.metadata,
                        isValid = isValid,
                        errorMessage = errorMsg
                    )
                )
            }

            config.devices.forEach { (subName, subConfig) ->
                visitBindings(containerPath + subName.parseAsName(), subConfig)
            }
        }

        visitBindings(Name.EMPTY, rootConfiguration)
        return wires
    }

    fun getCanvasHierarchyEdges(): List<CanvasHierarchyEdge> {
        val edges = mutableListOf<CanvasHierarchyEdge>()

        fun visitHierarchy(path: Name, config: ConstructorDeviceConfiguration) {
            val parentNodeId = if (path == Name.EMPTY) "root" else path.toString()

            config.devices.forEach { (subName, subConfig) ->
                val childPath = path + subName.parseAsName()
                val childNodeId = childPath.toString()
                edges.add(
                    CanvasHierarchyEdge(
                        id = "h:$parentNodeId->$childNodeId",
                        parentNodeId = parentNodeId,
                        parentPath = path,
                        childNodeId = childNodeId,
                        childPath = childPath,
                        childName = subName,
                        isTemplate = false
                    )
                )
                visitHierarchy(childPath, subConfig)
            }

            config.components.forEach { (tmplName, _) ->
                val tmplId = if (path == Name.EMPTY) "tmpl:$tmplName" else "$path:tmpl:$tmplName"
                val childPath = path + tmplName.parseAsName()
                edges.add(
                    CanvasHierarchyEdge(
                        id = "h:$parentNodeId->$tmplId",
                        parentNodeId = parentNodeId,
                        parentPath = path,
                        childNodeId = tmplId,
                        childPath = childPath,
                        childName = tmplName,
                        isTemplate = true
                    )
                )
            }
        }

        visitHierarchy(Name.EMPTY, rootConfiguration)
        return edges
    }

    fun validate(): List<ConfiguratorDiagnostic> {
        val diagnostics = mutableListOf<ConfiguratorDiagnostic>()

        fun validateDevice(path: Name, config: ConstructorDeviceConfiguration) {
            val pathStr = if (path == Name.EMPTY) "Root" else path.toString()

            if (path != Name.EMPTY) {
                val lastSegment = path.tokens.lastOrNull()?.toString() ?: ""
                try {
                    val parsed = lastSegment.parseAsName()
                    if (parsed == Name.EMPTY) {
                        diagnostics.add(
                            ConfiguratorDiagnostic(
                                severity = DiagnosticSeverity.ERROR,
                                message = "Invalid device name '$lastSegment' at $pathStr",
                                target = ConfiguratorSelection.DeviceNode(path)
                            )
                        )
                    }
                } catch (e: Exception) {
                    diagnostics.add(
                        ConfiguratorDiagnostic(
                            severity = DiagnosticSeverity.ERROR,
                            message = "Device name syntax error at $pathStr: ${e.message}",
                            target = ConfiguratorSelection.DeviceNode(path)
                        )
                    )
                }
            }

            config.properties.forEach { (propName, propConfig) ->
                try {
                    val parsed = propName.parseAsName()
                    if (parsed == Name.EMPTY) {
                        diagnostics.add(
                            ConfiguratorDiagnostic(
                                severity = DiagnosticSeverity.ERROR,
                                message = "Invalid property name '$propName' in $pathStr",
                                target = ConfiguratorSelection.Property(path, propName)
                            )
                        )
                    }
                } catch (e: Exception) {
                    diagnostics.add(
                        ConfiguratorDiagnostic(
                            severity = DiagnosticSeverity.ERROR,
                            message = "Property name '$propName' error in $pathStr: ${e.message}",
                            target = ConfiguratorSelection.Property(path, propName)
                        )
                    )
                }

                if (propConfig.type.isBlank()) {
                    diagnostics.add(
                        ConfiguratorDiagnostic(
                            severity = DiagnosticSeverity.ERROR,
                            message = "Property '$propName' in $pathStr has empty type",
                            target = ConfiguratorSelection.Property(path, propName)
                        )
                    )
                }

                if (propConfig.type == "tagTable" && tagTableConfiguration != null) {
                    val tag = propConfig.parameters["tag"]?.string
                    if (tag.isNullOrBlank()) {
                        diagnostics.add(
                            ConfiguratorDiagnostic(
                                severity = DiagnosticSeverity.WARNING,
                                message = "Property '$propName' has tagTable type but missing 'tag' parameter",
                                target = ConfiguratorSelection.Property(path, propName)
                            )
                        )
                    } else if (tagTableConfiguration?.properties?.containsKey(tag) == false) {
                        diagnostics.add(
                            ConfiguratorDiagnostic(
                                severity = DiagnosticSeverity.WARNING,
                                message = "Tag '$tag' referenced in '$propName' is not found in loaded TagTable",
                                target = ConfiguratorSelection.Property(path, propName)
                            )
                        )
                    }
                }
            }

            config.components.forEach { (tmplName, tmplConfig) ->
                if (tmplConfig.type.isBlank()) {
                    diagnostics.add(
                        ConfiguratorDiagnostic(
                            severity = DiagnosticSeverity.ERROR,
                            message = "Template device '$tmplName' in $pathStr has empty factory type",
                            target = ConfiguratorSelection.TemplateNode(path, tmplName)
                        )
                    )
                }
            }

            config.bindings.forEach { binding ->
                val sourceConfig = if (binding.sourceDevice == Name.EMPTY) config else config.devices[binding.sourceDevice.toString()]
                if (sourceConfig == null || !sourceConfig.properties.containsKey(binding.sourceProperty)) {
                    diagnostics.add(
                        ConfiguratorDiagnostic(
                            severity = DiagnosticSeverity.ERROR,
                            message = "Binding source '${binding.sourceDevice}.${binding.sourceProperty}' not found in $pathStr",
                            target = ConfiguratorSelection.Wire(binding.sourceDevice, binding.sourceProperty, binding.targetDevice, binding.targetInput)
                        )
                    )
                }

                val targetExists = config.components.containsKey(binding.targetDevice.toString()) ||
                        config.devices.containsKey(binding.targetDevice.toString())
                if (!targetExists) {
                    diagnostics.add(
                        ConfiguratorDiagnostic(
                            severity = DiagnosticSeverity.ERROR,
                            message = "Binding target '${binding.targetDevice}' not found in $pathStr",
                            target = ConfiguratorSelection.Wire(binding.sourceDevice, binding.sourceProperty, binding.targetDevice, binding.targetInput)
                        )
                    )
                }
            }

            config.devices.forEach { (subName, subConfig) ->
                validateDevice(path + subName.parseAsName(), subConfig)
            }
        }

        validateDevice(Name.EMPTY, rootConfiguration)
        return diagnostics
    }

    fun computeAutoLayout() {
        var currentX = 60f
        var currentY = 60f
        val columnWidth = 320f
        val rowHeight = 220f

        fun layoutRec(path: Name, config: ConstructorDeviceConfiguration, level: Int) {
            val nodeId = if (path == Name.EMPTY) "root" else path.toString()
            nodePositions[nodeId] = Offset(currentX, currentY)

            var tmplY = currentY
            config.components.forEach { (tmplName, _) ->
                val tmplId = if (path == Name.EMPTY) "tmpl:$tmplName" else "$path:tmpl:$tmplName"
                nodePositions[tmplId] = Offset(currentX + columnWidth, tmplY)
                tmplY += 160f
            }

            val maxUsedY = max(currentY + rowHeight, tmplY + 20f)
            currentY = maxUsedY

            config.devices.forEach { (subName, subConfig) ->
                currentX += 40f
                layoutRec(path + subName.parseAsName(), subConfig, level + 1)
                currentX -= 40f
            }
        }

        layoutRec(Name.EMPTY, rootConfiguration, 0)
    }

    fun exportSchemeJson(): String {
        return configuratorJson.encodeToString(ConstructorDeviceConfiguration.serializer(), rootConfiguration)
    }

    fun importSchemeJson(jsonString: String) {
        val parsed = configuratorJson.decodeFromString(ConstructorDeviceConfiguration.serializer(), jsonString)
        setConfiguration(parsed)
    }

    fun importTagTableJson(jsonString: String) {
        val parsed = configuratorJson.decodeFromString(TagTableConfiguration.serializer(), jsonString)
        setTagTable(parsed)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun importSchemeFromFile(path: Path) {
        val parsed = path.inputStream().use {
            configuratorJson.decodeFromStream(ConstructorDeviceConfiguration.serializer(), it)
        }
        currentSchemePath = path
        setConfiguration(parsed)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun importTagTableFromFile(path: Path) {
        val parsed = path.inputStream().use {
            configuratorJson.decodeFromStream(TagTableConfiguration.serializer(), it)
        }
        currentTagTablePath = path
        setTagTable(parsed)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun exportSchemeToFile(path: Path) {
        path.outputStream().use {
            configuratorJson.encodeToStream(ConstructorDeviceConfiguration.serializer(), rootConfiguration, it)
        }
        currentSchemePath = path
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun exportTagTableToFile(path: Path) {
        val tt = tagTableConfiguration ?: return
        path.outputStream().use {
            configuratorJson.encodeToStream(TagTableConfiguration.serializer(), tt, it)
        }
        currentTagTablePath = path
    }
}
