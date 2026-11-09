package space.kscience.controls.constructor

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.last
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class ConstructorPluginTest {



    private class ConstantFactory(private val value: Double) : ValueStateFactory {
        override val descriptor: MetaDescriptor? = null

        override fun build(context: Context, meta: Meta): ValueState<Meta> = ValueState(Meta(value))
    }

    private class ValueFactoryPlugin(pluginName: String, value: Double) : AbstractPlugin() {
        override val tag: PluginTag = PluginTag(pluginName)
        private val factory = ConstantFactory(value)

        override fun content(target: String): Map<Name, Any> = when (target) {
            ValueStateFactory.PROVIDER_TAGET -> mapOf(Name.of("constant") to factory)
            else -> super.content(target)
        }
    }


    @Test
    fun testConstructRejectsAmbiguousShortFactoryName() = runTest(timeout = 5.seconds) {
        val context = Context("ambiguous-value-factories") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ValueFactoryPlugin("b", 2.0))
            plugin(ValueFactoryPlugin("a", 1.0))
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val error = assertFailsWith<IllegalStateException> {
                constructor.construct(
                    ConstructorDeviceConfiguration(
                        properties = mapOf("value" to ValueStateConfiguration("constant", Meta.EMPTY)),
                    ),
                )
            }
            assertEquals("Value state factory type constant is ambiguous: [a.constant, b.constant]", error.message)

            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "first" to ValueStateConfiguration("a.constant", Meta.EMPTY),
                        "second" to ValueStateConfiguration("b.constant", Meta.EMPTY),
                    ),
                ),
            )
            assertEquals(1.0, tree.getCachedProperty("first")?.double)
            assertEquals(2.0, tree.getCachedProperty("second")?.double)
            assertContains(constructor.valueStateFactories.keys.map { it.last().toString() }, "constant")
        } finally {
            context.close()
        }
    }

    @Test
    fun testJsonSerializationRoundTrip() {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        val jsonText = """
        {
          "properties": {
            "temperature": {
              "type": "tagTable",
              "parameters": {
                "tag": "opc.sensor.temp"
              }
            }
          },
          "components": {
            "tempAlarm": {
              "type": "controls.utilities.alarm",
              "parameters": {
                "settings": [
                  {
                    "lowerThreshold": 10.0,
                    "upperThreshold": 80.0,
                    "status": "WARN"
                  }
                ]
              }
            }
          },
          "bindings": [
            {
              "sourceDevice": "",
              "sourceProperty": "temperature",
              "targetDevice": "tempAlarm",
              "targetInput": "default"
            }
          ],
          "devices": {
            "subPump": {
              "properties": {
                "speed": {
                  "type": "tagTable",
                  "parameters": {
                    "tag": "modbus.pump.speed"
                  }
                }
              }
            }
          }
        }
        """.trimIndent()

        val parsed = json.decodeFromString(ConstructorDeviceConfiguration.serializer(), jsonText)
        assertNotNull(parsed.properties["temperature"])
        assertEquals("tagTable", parsed.properties["temperature"]?.type)
        assertNotNull(parsed.components["tempAlarm"])
        assertEquals("controls.utilities.alarm", parsed.components["tempAlarm"]?.type)
        assertEquals(1, parsed.bindings.size)
        assertEquals(Name.EMPTY, parsed.bindings.first().sourceDevice)
        assertEquals("temperature", parsed.bindings.first().sourceProperty)
        assertEquals(Name.of("tempAlarm"), parsed.bindings.first().targetDevice)
        assertNotNull(parsed.devices["subPump"])

        val encoded = json.encodeToString(ConstructorDeviceConfiguration.serializer(), parsed)
        val reparsed = json.decodeFromString(ConstructorDeviceConfiguration.serializer(), encoded)
        assertEquals(parsed.properties.keys, reparsed.properties.keys)
        assertEquals(parsed.components.keys, reparsed.components.keys)
        assertEquals(parsed.devices.keys, reparsed.devices.keys)
    }
}
