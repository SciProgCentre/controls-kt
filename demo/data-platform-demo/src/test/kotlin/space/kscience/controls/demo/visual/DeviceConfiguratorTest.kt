/*
 * LLM generated code: Unit tests for Device Scheme Visual Configurator model, validation, and JSON serialization.
 */
package space.kscience.controls.demo.visual

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import java.nio.file.Files
import kotlin.test.*
import space.kscience.controls.constructor.ConstructorBinding
import space.kscience.controls.demo.loadDeviceConfiguration
import space.kscience.controls.demo.loadTagTableConfiguration
import space.kscience.controls.demo.visual.configurator.DeviceConfiguratorModel
import space.kscience.controls.demo.visual.configurator.DiagnosticSeverity
import space.kscience.controls.tagtable.OpcTagTableColumn
import space.kscience.controls.tagtable.TagTableConfiguration
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName

class DeviceConfiguratorTest {

    @Test
    fun testInitialStateAndAutoLayout() {
        val model = DeviceConfiguratorModel(context = Global)
        val nodes = model.getCanvasNodes()
        assertEquals(1, nodes.size, "Should have root node initially")
        assertEquals("root", nodes.first().id)
        assertTrue(nodes.first().outputPorts.isEmpty())
    }

    @Test
    fun testAddDeviceBlockAndProperties() {
        val model = DeviceConfiguratorModel(context = Global)

        // 1. Add device block 'part0' under root
        model.addDeviceBlock(Name.EMPTY, "part0")
        val part0Path = "part0".parseAsName()

        assertNotNull(model.findDeviceConfig(part0Path))
        val nodesAfterAdd = model.getCanvasNodes()
        assertEquals(2, nodesAfterAdd.size)

        // 2. Add property 'temp' with tagTable type
        model.addProperty(
            devicePath = part0Path,
            propertyName = "temp",
            factoryType = "tagTable",
            parameters = Meta { "tag" put "opc.sensor.temp" }
        )

        val part0Config = model.findDeviceConfig(part0Path)
        assertNotNull(part0Config)
        assertTrue(part0Config.properties.containsKey("temp"))
        assertEquals("tagTable", part0Config.properties["temp"]?.type)

        // 3. Add computed property 'sum'
        model.addProperty(
            devicePath = part0Path,
            propertyName = "sum",
            factoryType = "expression",
            parameters = Meta { "expression" put "temp * 2" }
        )

        assertEquals(2, model.findDeviceConfig(part0Path)!!.properties.size)
    }

    @Test
    fun testAddTemplateDeviceAndWiring() {
        val model = DeviceConfiguratorModel(context = Global)

        // Add device block
        model.addDeviceBlock(Name.EMPTY, "sensorBlock")
        val sensorPath = "sensorBlock".parseAsName()
        model.addProperty(sensorPath, "reading", "tagTable", Meta { "tag" put "temp1" })

        // Add template device 'alarm1'
        model.addTemplateDevice(
            parentPath = Name.EMPTY,
            name = "alarm1",
            factoryType = "controls.utilities.alarm",
            parameters = Meta {
                "min" put 0.0
                "max" put 100.0
            }
        )

        val root = model.rootConfiguration
        assertTrue(root.components.containsKey("alarm1"))
        assertEquals("controls.utilities.alarm", root.components["alarm1"]?.type)

        // Wire 'sensorBlock.reading' to 'alarm1.default'
        val binding = ConstructorBinding(
            sourceDevice = sensorPath,
            sourceProperty = "reading",
            targetDevice = Name.of("alarm1"),
            targetInput = ""
        )
        model.addBinding(binding)

        val wires = model.getCanvasWires()
        assertEquals(1, wires.size)
        assertTrue(wires.first().isValid)
        assertEquals(sensorPath, wires.first().sourceDeviceName)
        assertEquals("reading", wires.first().sourceProperty)
        assertEquals("alarm1", wires.first().targetDeviceName.toString())
    }

    @Test
    fun testValidationDiagnostics() {
        val model = DeviceConfiguratorModel(context = Global)

        // Create broken binding (target device doesn't exist)
        val brokenBinding = ConstructorBinding(
            sourceDevice = Name.EMPTY,
            sourceProperty = "nonExistingProp",
            targetDevice = Name.of("nonExistingTarget"),
            targetInput = ""
        )
        model.addBinding(brokenBinding)

        val diagnostics = model.validate()
        val errors = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }
        assertTrue(errors.isNotEmpty(), "Validation should detect missing source property and missing target device")

        // Set TagTable and check tag validation warning
        model.addProperty(Name.EMPTY, "opcProp", "tagTable", Meta { "tag" put "unregistered.tag" })
        val tagTable = TagTableConfiguration(
            sources = emptyMap(),
            timers = emptyMap(),
            properties = mapOf(
                "registered.tag" to OpcTagTableColumn(source = "opc", timer = "default", nodeId = "ns=2;s=Temp")
            )
        )
        model.setTagTable(tagTable)

        val warnings = model.validate().filter { it.severity == DiagnosticSeverity.WARNING }
        assertTrue(warnings.any { it.message.contains("unregistered.tag") })
    }

    @Test
    fun testUndoRedo() {
        val model = DeviceConfiguratorModel(context = Global)
        assertFalse(model.canUndo)

        model.addDeviceBlock(Name.EMPTY, "dev1")
        assertTrue(model.canUndo)
        assertNotNull(model.findDeviceConfig("dev1".parseAsName()))

        model.undo()
        assertNull(model.findDeviceConfig("dev1".parseAsName()))
        assertTrue(model.canRedo)

        model.redo()
        assertNotNull(model.findDeviceConfig("dev1".parseAsName()))
    }

    @Test
    fun testJsonExportAndImportRoundTrip() {
        val model = DeviceConfiguratorModel(context = Global)
        model.addDeviceBlock(Name.EMPTY, "deviceA")
        val devPath = "deviceA".parseAsName()
        model.addProperty(devPath, "val1", "tagTable", Meta { "tag" put "t1" })
        model.addTemplateDevice(Name.EMPTY, "alarmA", "controls.utilities.alarm")

        val exportedJson = model.exportSchemeJson()
        assertTrue(exportedJson.contains("deviceA"))
        assertTrue(exportedJson.contains("val1"))
        assertTrue(exportedJson.contains("alarmA"))

        val newModel = DeviceConfiguratorModel(context = Global)
        newModel.importSchemeJson(exportedJson)

        assertNotNull(newModel.findDeviceConfig(devPath))
        assertTrue(newModel.rootConfiguration.components.containsKey("alarmA"))
        assertEquals("tagTable", newModel.findDeviceConfig(devPath)!!.properties["val1"]?.type)
    }

    @Test
    fun testCascadingRemovalCleansBindings() {
        val model = DeviceConfiguratorModel(context = Global)
        model.addDeviceBlock(Name.EMPTY, "devX")
        val devPath = "devX".parseAsName()
        model.addProperty(devPath, "p1", "tagTable")
        model.addTemplateDevice(Name.EMPTY, "tmplX", "test.factory")

        model.addBinding(
            ConstructorBinding(
                sourceDevice = devPath,
                sourceProperty = "p1",
                targetDevice = Name.of("tmplX"),
                targetInput = ""
            )
        )
        assertEquals(1, model.rootConfiguration.bindings.size)

        // Removing devX should clean its binding
        model.removeDeviceBlock(devPath)
        assertEquals(0, model.rootConfiguration.bindings.size)
    }

    @Test
    fun testFileExportAndImport() {
        val tempDir = Files.createTempDirectory("configurator_test")
        try {
            val schemeFile = tempDir.resolve("device-config.json")
            val tagTableFile = tempDir.resolve("platform-config.json")

            val model = DeviceConfiguratorModel(context = Global)
            model.addDeviceBlock(Name.EMPTY, "motor1")
            val motorPath = "motor1".parseAsName()
            model.addProperty(motorPath, "speed", "tagTable")
            model.exportSchemeToFile(schemeFile)

            assertTrue(Files.exists(schemeFile))
            val loadedConfig = loadDeviceConfiguration(schemeFile)
            assertTrue(loadedConfig.devices.containsKey("motor1"))

            val newModel = DeviceConfiguratorModel(context = Global)
            newModel.importSchemeFromFile(schemeFile)
            assertNotNull(newModel.findDeviceConfig(motorPath))

            val sampleTagTable = TagTableConfiguration(
                sources = emptyMap(),
                timers = emptyMap(),
                properties = mapOf("sens.temp" to OpcTagTableColumn(source = "src1", timer = "t1", nodeId = "node.temp"))
            )
            val modelWithTT = DeviceConfiguratorModel(context = Global, initialTagTable = sampleTagTable)
            modelWithTT.exportTagTableToFile(tagTableFile)

            assertTrue(Files.exists(tagTableFile))
            val loadedTT = loadTagTableConfiguration(tagTableFile)
            assertTrue(loadedTT.properties.containsKey("sens.temp"))

            val modelImportTT = DeviceConfiguratorModel(context = Global)
            modelImportTT.importTagTableFromFile(tagTableFile)
            assertNotNull(modelImportTT.tagTableConfiguration)
            assertTrue(modelImportTT.tagTableConfiguration!!.properties.containsKey("sens.temp"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun testNodePositionUpdates() {
        val model = DeviceConfiguratorModel(context = Global)
        model.addDeviceBlock(Name.EMPTY, "blockA")
        val blockId = "blockA"

        val initialPos = model.nodePositions[blockId] ?: Offset.Zero
        val newPos = initialPos + Offset(15.5f, 25.5f)
        model.updateNodePosition(blockId, newPos)

        assertEquals(newPos, model.nodePositions[blockId])
    }

    @Test
    fun testHierarchyEdgesForSubdevicesAndComponents() {
        val model = DeviceConfiguratorModel(context = Global)

        // 1. Add sub-device 'part0' under root
        model.addDeviceBlock(Name.EMPTY, "part0")
        val part0Path = "part0".parseAsName()

        // 2. Add nested sub-device 'motor' under 'part0'
        model.addDeviceBlock(part0Path, "motor")

        // 3. Add template component 'alarm1' under 'part0'
        model.addTemplateDevice(part0Path, "alarm1", "controls.utilities.alarm")

        // 4. Add template component 'rootAlarm' under root
        model.addTemplateDevice(Name.EMPTY, "rootAlarm", "controls.utilities.alarm")

        val edges = model.getCanvasHierarchyEdges()
        assertEquals(4, edges.size)

        val rootToPart0 = edges.firstOrNull { it.parentNodeId == "root" && it.childNodeId == "part0" }
        assertNotNull(rootToPart0)
        assertFalse(rootToPart0.isTemplate)
        assertEquals("part0", rootToPart0.childName)

        val part0ToMotor = edges.firstOrNull { it.parentNodeId == "part0" && it.childNodeId == "part0.motor" }
        assertNotNull(part0ToMotor)
        assertFalse(part0ToMotor.isTemplate)
        assertEquals("motor", part0ToMotor.childName)

        val part0ToAlarm1 = edges.firstOrNull { it.parentNodeId == "part0" && it.childNodeId == "part0:tmpl:alarm1" }
        assertNotNull(part0ToAlarm1)
        assertTrue(part0ToAlarm1.isTemplate)
        assertEquals("alarm1", part0ToAlarm1.childName)

        val rootToRootAlarm = edges.firstOrNull { it.parentNodeId == "root" && it.childNodeId == "tmpl:rootAlarm" }
        assertNotNull(rootToRootAlarm)
        assertTrue(rootToRootAlarm.isTemplate)
        assertEquals("rootAlarm", rootToRootAlarm.childName)
    }

    @Test
    fun testHierarchyEdgeRemovalOnDeviceDeletion() {
        val model = DeviceConfiguratorModel(context = Global)
        model.addDeviceBlock(Name.EMPTY, "partX")
        val partXPath = "partX".parseAsName()
        model.addDeviceBlock(partXPath, "childSub")
        model.addTemplateDevice(partXPath, "childTmpl", "controls.utilities.alarm")

        assertEquals(3, model.getCanvasHierarchyEdges().size)

        model.removeDeviceBlock(partXPath)
        assertEquals(0, model.getCanvasHierarchyEdges().size)
    }

    @Test
    fun testNodeSizeUpdates() {
        val model = DeviceConfiguratorModel(context = Global)
        model.updateNodeSize("root", IntSize(300, 200))
        assertEquals(IntSize(300, 200), model.nodeSizes["root"])
    }
}
