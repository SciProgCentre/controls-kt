package space.kscience.controls.utilities

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.map
import space.kscience.controls.nullable
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.ValueRestriction
import space.kscience.dataforge.meta.descriptors.get
import space.kscience.dataforge.names.Name
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class AlarmTest {

    private fun ValueState<Double?>.asMeta(): ValueState<Meta> = map(MetaConverter.double.nullable()::convert)

    private fun settingsMeta(vararg settings: Meta): Meta = Meta {
        setIndexed(Name.of("setting"), settings.toList())
    }

    private suspend fun TestScope.withTestContext(name: String, block: suspend (Context) -> Unit) {
        val context = Context(name) {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            block(context)
        } finally {
            context.close()
        }
    }

    @Test
    fun testAlarmSettingValidation() {
        val settingLowerOnly = AlarmSetting(lowerThreshold = 10.0, upperThreshold = null, status = "LOW")
        assertEquals(10.0, settingLowerOnly.lowerThreshold)
        assertNull(settingLowerOnly.upperThreshold)
        assertEquals("LOW", settingLowerOnly.status)

        val settingUpperOnly = AlarmSetting(lowerThreshold = null, upperThreshold = 50.0, status = "HIGH")
        assertNull(settingUpperOnly.lowerThreshold)
        assertEquals(50.0, settingUpperOnly.upperThreshold)
        assertEquals("HIGH", settingUpperOnly.status)

        val settingBoth = AlarmSetting(lowerThreshold = 10.0, upperThreshold = 50.0, status = "OUT_OF_BOUNDS")
        assertEquals(10.0, settingBoth.lowerThreshold)
        assertEquals(50.0, settingBoth.upperThreshold)
        assertEquals("OUT_OF_BOUNDS", settingBoth.status)

        assertFailsWith<IllegalArgumentException> {
            AlarmSetting(lowerThreshold = null, upperThreshold = null, status = "INVALID")
        }
        assertFailsWith<IllegalArgumentException> {
            AlarmSetting(status = "INVALID")
        }
    }

    @Test
    fun testAlarmSettingsConverterWithExplicitNullThresholds() {
        val input = settingsMeta(
            Meta {
                "lowerThreshold" put 10.0
                "upperThreshold" put Meta(Null)
                "status" put "LOW"
            },
            Meta {
                "lowerThreshold" put Meta(Null)
                "upperThreshold" put 40.0
                "status" put "HIGH"
            }
        )
        val expected = listOf(
            AlarmSetting(lowerThreshold = 10.0, status = "LOW"),
            AlarmSetting(upperThreshold = 40.0, status = "HIGH")
        )
        val decoded = Alarm.settingsConverter.read(input)
        assertEquals(expected, decoded)
        assertEquals(expected, Alarm.settingsConverter.read(Alarm.settingsConverter.convert(decoded)))
    }

    @Test
    fun testAlarmSettingsConverterWithOmittedThresholds() {
        val input = settingsMeta(
            Meta {
                "lowerThreshold" put 10.0
                "status" put "LOW"
            },
            Meta {
                "upperThreshold" put 40.0
                "status" put "HIGH"
            },
            Meta {
                "lowerThreshold" put 0.0
                "upperThreshold" put 60.0
                "status" put "OUT_OF_BOUNDS"
            }
        )
        val expected = listOf(
            AlarmSetting(lowerThreshold = 10.0, status = "LOW"),
            AlarmSetting(upperThreshold = 40.0, status = "HIGH"),
            AlarmSetting(lowerThreshold = 0.0, upperThreshold = 60.0, status = "OUT_OF_BOUNDS")
        )
        val decoded = Alarm.settingsConverter.read(input)
        assertEquals(expected, decoded)
        assertEquals(expected, Alarm.settingsConverter.read(Alarm.settingsConverter.convert(decoded)))
    }

    @Test
    fun testAlarmSettingsConverterRejectsMissingThresholds() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Alarm.settingsConverter.read(settingsMeta(Meta { "status" put "INVALID" }))
        }
        assertEquals("At least one threshold must be defined", exception.message)
    }

    @Test
    fun testAlarmParameterDescriptor() {
        val setting = assertNotNull(Alarm.descriptor["settings"]?.get("setting"))
        assertTrue(setting.multiple)
        assertEquals(
            listOf(ValueType.NUMBER),
            assertNotNull(setting["lowerThreshold"]).valueTypes
        )
        assertEquals(
            listOf(ValueType.NUMBER),
            assertNotNull(setting["upperThreshold"]).valueTypes
        )
        val status = assertNotNull(setting["status"])
        assertEquals(listOf(ValueType.STRING), status.valueTypes)
        assertEquals(ValueRestriction.REQUIRED, status.valueRestriction)
        assertNotNull(Alarm.descriptor[DeviceConstructor.METADATA_KEY])
    }

    @Test
    fun testAlarmUnboundState() = runTest(timeout = 5.seconds) {
        withTestContext("alarmUnbound") { context ->
            val alarm = Alarm(context)
            assertEquals(AlarmState(Alarm.STATUS_UNDEFINED, null), alarm.state.value)
        }
    }

    @Test
    fun testAlarmInitialStateNullValue() = runTest(timeout = 5.seconds) {
        withTestContext("alarmInitialNull") { context ->
            val alarm = Alarm(context)
            alarm.bind(ValueState<Double?>(null).asMeta())
            assertEquals(AlarmState(Alarm.STATUS_UNDEFINED, null), alarm.state.value)
        }
    }

    @Test
    fun testAlarmInitialStateWithoutSettings() = runTest(timeout = 5.seconds) {
        withTestContext("alarmWithoutSettings") { context ->
            val alarm = Alarm(context)
            alarm.bind(ValueState<Double?>(25.0).asMeta())
            assertEquals(AlarmState(Alarm.STATUS_OK, 25.0), alarm.state.value)
        }
    }

    @Test
    fun testAlarmThresholdEvaluations() = runTest(timeout = 5.seconds) {
        withTestContext("alarmThresholds") { context ->
            val source = MutableValueState<Double?>(null)
            val alarm = Alarm(
                context,
                listOf(
                    AlarmSetting(lowerThreshold = 10.0, upperThreshold = null, status = "LOW_WARN"),
                    AlarmSetting(lowerThreshold = 0.0, upperThreshold = null, status = "LOW_ERROR"),
                    AlarmSetting(lowerThreshold = null, upperThreshold = 40.0, status = "HIGH_WARN"),
                    AlarmSetting(lowerThreshold = null, upperThreshold = 60.0, status = "HIGH_ERROR")
                )
            )
            alarm.bind(source.asMeta())

            fun assertState(value: Double?, status: String) {
                source.value = value
                assertEquals(AlarmState(status, value), alarm.state.value)
            }

            assertState(null, Alarm.STATUS_UNDEFINED)
            assertState(25.0, Alarm.STATUS_OK)
            assertState(10.0, Alarm.STATUS_OK)
            assertState(40.0, Alarm.STATUS_OK)
            assertState(5.0, "LOW_WARN")
            assertState(-5.0, "LOW_ERROR")
            assertState(50.0, "HIGH_WARN")
            assertState(70.0, "HIGH_ERROR")
            assertState(null, Alarm.STATUS_UNDEFINED)
        }
    }

    @Test
    fun testAlarmDynamicSettingsChange() = runTest(timeout = 5.seconds) {
        withTestContext("alarmDynamicSettings") { context ->
            val source = MutableValueState<Double?>(30.0)
            val alarm = Alarm(context)
            alarm.bind(source.asMeta())
            assertEquals(AlarmState(Alarm.STATUS_OK, 30.0), alarm.state.value)

            alarm.alarmSettings.value = listOf(
                AlarmSetting(lowerThreshold = null, upperThreshold = 25.0, status = "WARNING")
            )
            assertEquals(AlarmState("WARNING", 30.0), alarm.state.value)

            alarm.alarmSettings.value = listOf(
                AlarmSetting(lowerThreshold = null, upperThreshold = 35.0, status = "WARNING")
            )
            assertEquals(AlarmState(Alarm.STATUS_OK, 30.0), alarm.state.value)
        }
    }

    @Test
    fun testAlarmRegisteredPropertyState() = runTest(timeout = 5.seconds) {
        withTestContext("alarmRegisteredProperty") { context ->
            val alarm = Alarm(context)
            alarm.bind(MutableValueState<Double?>(50.0).asMeta())

            val initialMeta = alarm.readProperty("state")
            assertEquals(Alarm.STATUS_OK, initialMeta["message"].string)
            assertEquals(50.0, initialMeta["value"].double)

            alarm.alarmSettings.value = listOf(
                AlarmSetting(lowerThreshold = null, upperThreshold = 40.0, status = "HOT")
            )
            val hotMeta = alarm.readProperty("state")
            assertEquals("HOT", hotMeta["message"].string)
            assertEquals(50.0, hotMeta["value"].double)
        }
    }

    @Test
    fun testAlarmFactoryBuildDevice() = runTest(timeout = 5.seconds) {
        withTestContext("alarmFactory") { context ->
            val settings = listOf(
                AlarmSetting(lowerThreshold = null, upperThreshold = 30.0, status = "TOO_HOT")
            )
            val metadata = Meta { "description" put "Temperature alarm" }
            val parameters = Alarm.buildDeviceMeta(settings, metadata)
            assertEquals(settings, parameters[Alarm.settings])
            assertTrue(Meta.equals(metadata, parameters[Alarm.metadata]))
            val alarm = Alarm.buildDevice(context, parameters)

            assertEquals(settings, alarm.alarmSettings.value)
            assertEquals("Temperature alarm", alarm.meta["description"].string)
            assertEquals(AlarmState(Alarm.STATUS_UNDEFINED, null), alarm.state.value)

            val source = MutableValueState<Double?>(20.0)
            alarm.bind(source.asMeta())
            assertEquals(AlarmState(Alarm.STATUS_OK, 20.0), alarm.state.value)

            source.value = 35.0
            assertEquals(AlarmState("TOO_HOT", 35.0), alarm.state.value)
        }
    }

    @Test
    fun testAlarmFactoryWithoutSettings() = runTest(timeout = 5.seconds) {
        withTestContext("alarmFactoryWithoutSettings") { context ->
            val alarm = Alarm.buildDevice(context, Meta.EMPTY)
            assertEquals(emptyList(), alarm.alarmSettings.value)
            assertEquals(AlarmState(Alarm.STATUS_UNDEFINED, null), alarm.state.value)
        }
    }

    @Test
    fun testAlarmBindingInputNames() = runTest(timeout = 5.seconds) {
        withTestContext("alarmInputNames") { context ->
            val settings = listOf(
                AlarmSetting(lowerThreshold = null, upperThreshold = 30.0, status = "HOT")
            )
            val source = MutableValueState<Double?>(25.0)
            val defaultInput = Alarm(context, settings)
            val namedInput = Alarm(context, settings)
            defaultInput.bind(source.asMeta())
            namedInput.bind(source.asMeta(), "value")

            assertEquals(AlarmState(Alarm.STATUS_OK, 25.0), defaultInput.state.value)
            assertEquals(defaultInput.state.value, namedInput.state.value)
            source.value = 35.0
            assertEquals(AlarmState("HOT", 35.0), defaultInput.state.value)
            assertEquals(defaultInput.state.value, namedInput.state.value)
        }
    }

    @Test
    fun testAlarmRejectsUnknownInput() = runTest(timeout = 5.seconds) {
        withTestContext("alarmUnknownInput") { context ->
            val alarm = Alarm(context)
            val source = ValueState<Double?>(10.0).asMeta()
            assertFailsWith<IllegalStateException> {
                alarm.bind(source, "other")
            }

            alarm.bind(source)
            assertEquals(AlarmState(Alarm.STATUS_OK, 10.0), alarm.state.value)
        }
    }

    @Test
    fun testAlarmRejectsRepeatedBinding() = runTest(timeout = 5.seconds) {
        withTestContext("alarmRepeatedBinding") { context ->
            val alarm = Alarm(context)
            alarm.bind(ValueState<Double?>(10.0).asMeta())
            val exception = assertFailsWith<IllegalStateException> {
                alarm.bind(ValueState<Double?>(20.0).asMeta(), "value")
            }

            assertEquals("The state is already bound", exception.message)
            assertEquals(AlarmState(Alarm.STATUS_OK, 10.0), alarm.state.value)
        }
    }
}
