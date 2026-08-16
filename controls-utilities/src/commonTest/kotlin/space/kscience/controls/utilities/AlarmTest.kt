package space.kscience.controls.utilities

/*
 * LLM generated code: Comprehensive unit tests for Alarm device and AlarmSetting in controls-utilities module.
 */

import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.nullable
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AlarmTest {

    private class TestSourceDevice(context: Context) : DeviceConstructor(context) {
        val temperature: MutableValueState<Double?> by virtualProperty(
            MetaConverter.double.nullable(),
            initialState = 20.0
        )
    }

    private val testContext = Context("test")

    @Test
    fun testAlarmSettingValidation() {
        // Valid settings
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

        // Invalid setting: both thresholds null
        assertFailsWith<IllegalArgumentException> {
            AlarmSetting(lowerThreshold = null, upperThreshold = null, status = "INVALID")
        }
    }

    @Test
    fun testAlarmInitialStateNullValue() {
        val valueState = ValueState<Double?>(null)
        val alarm = Alarm(testContext, valueState)

        assertEquals(Alarm.STATUS_UNDEFINED, alarm.state.value.message)
    }

    @Test
    fun testAlarmInitialStateWithoutSettings() {
        val valueState = ValueState<Double?>(25.0)
        val alarm = Alarm(testContext, valueState)

        assertEquals(Alarm.STATUS_OK, alarm.state.value.message)
    }

    @Test
    fun testAlarmThresholdEvaluations() = runTest {
        val valueState = MutableValueState<Double?>(null)
        val alarm = Alarm(testContext, valueState)

        alarm.alarmSettings.value = listOf(
            AlarmSetting(lowerThreshold = 10.0, upperThreshold = null, status = "LOW_WARN"),
            AlarmSetting(lowerThreshold = 0.0, upperThreshold = null, status = "LOW_ERROR"),
            AlarmSetting(lowerThreshold = null, upperThreshold = 40.0, status = "HIGH_WARN"),
            AlarmSetting(lowerThreshold = null, upperThreshold = 60.0, status = "HIGH_ERROR")
        )

        // Value is null -> UNDEFINED
        assertEquals(Alarm.STATUS_UNDEFINED, alarm.state.value.message)

        // Value within normal range -> OK
        valueState.value = 25.0
        assertEquals(Alarm.STATUS_OK, alarm.state.value.message)

        // Value at exact boundaries (lowerThreshold = 10.0, upperThreshold = 40.0) -> OK (since < and > are strict)
        valueState.value = 10.0
        assertEquals(Alarm.STATUS_OK, alarm.state.value.message)

        valueState.value = 40.0
        assertEquals(Alarm.STATUS_OK, alarm.state.value.message)

        // Value below lower threshold (5.0 < 10.0) -> LOW_WARN
        valueState.value = 5.0
        assertEquals("LOW_WARN", alarm.state.value.message)

        // Value below both lower thresholds (-5.0 < 10.0 and -5.0 < 0.0) -> last wins: LOW_ERROR
        valueState.value = -5.0
        assertEquals("LOW_ERROR", alarm.state.value.message)

        // Value above upper threshold (50.0 > 40.0) -> HIGH_WARN
        valueState.value = 50.0
        assertEquals("HIGH_WARN", alarm.state.value.message)

        // Value above both upper thresholds (70.0 > 40.0 and 70.0 > 60.0) -> last wins: HIGH_ERROR
        valueState.value = 70.0
        assertEquals("HIGH_ERROR", alarm.state.value.message)

        // Value back to null -> UNDEFINED
        valueState.value = null
        assertEquals(Alarm.STATUS_UNDEFINED, alarm.state.value.message)
    }

    @Test
    fun testAlarmDynamicSettingsChange() = runTest {
        val valueState = MutableValueState<Double?>(30.0)
        val alarm = Alarm(testContext, valueState)

        assertEquals(Alarm.STATUS_OK, alarm.state.value.message)

        alarm.alarmSettings.value = listOf(
            AlarmSetting(lowerThreshold = null, upperThreshold = 25.0, status = "WARNING")
        )

        assertEquals("WARNING", alarm.state.value.message)

        alarm.alarmSettings.value = listOf(
            AlarmSetting(lowerThreshold = null, upperThreshold = 35.0, status = "WARNING")
        )

        assertEquals(Alarm.STATUS_OK, alarm.state.value.message)
    }

    @Test
    fun testAlarmRegisteredPropertyState() = runTest {
        val valueState = MutableValueState<Double?>(50.0)
        val alarm = Alarm(testContext, valueState)

        val initialMeta = alarm.readProperty("state")
        assertEquals(Alarm.STATUS_OK, initialMeta["message"].string)

        alarm.alarmSettings.value = listOf(
            AlarmSetting(lowerThreshold = null, upperThreshold = 40.0, status = "HOT")
        )

        val hotMeta = alarm.readProperty("state")
        assertEquals("HOT", hotMeta["message"].string)
    }

    @Test
    fun testAlarmFactoryBuildDevice() = runTest {
        val context = Context("factoryTest") {
            plugin(DeviceManager)
        }
        val deviceManager = context.request(DeviceManager)
        val sourceDevice = deviceManager.install("sensor", TestSourceDevice(context))

        val meta = Meta {
            "deviceName" put "sensor"
            "propertyName" put "temperature"
        }

        val alarmDevice = Alarm.buildDevice(context, meta)
        assertEquals(Alarm.STATUS_OK, alarmDevice.state.value.message)

        alarmDevice.alarmSettings.value = listOf(
            AlarmSetting(lowerThreshold = null, upperThreshold = 30.0, status = "TOO_HOT")
        )
        assertEquals(Alarm.STATUS_OK, alarmDevice.state.value.message)

        sourceDevice.temperature.value = 35.0
        assertEquals("TOO_HOT", alarmDevice.state.value.message)
    }

    @Test
    fun testAlarmFactoryWithDottedDeviceName() = runTest {
        val context = Context("factoryDottedTest") {
            plugin(DeviceManager)
        }
        val deviceManager = context.request(DeviceManager)
        val group = deviceManager.install("group", DeviceConstructor(context))
        val sourceDevice = group.install("sensor", TestSourceDevice(context))

        val meta = Meta {
            "deviceName" put "group.sensor"
            "propertyName" put "temperature"
        }

        val alarmDevice = Alarm.buildDevice(context, meta)
        assertEquals(Alarm.STATUS_OK, alarmDevice.state.value.message)

        alarmDevice.alarmSettings.value = listOf(
            AlarmSetting(lowerThreshold = null, upperThreshold = 30.0, status = "TOO_HOT")
        )
        sourceDevice.temperature.value = 35.0
        assertEquals("TOO_HOT", alarmDevice.state.value.message)
    }

    @Test
    fun testAlarmFactoryMissingParameters() {
        val context = Context("factoryTest") {
            plugin(DeviceManager)
        }

        assertFailsWith<IllegalStateException> {
            Alarm.buildDevice(context, Meta { "propertyName" put "temp" })
        }

        assertFailsWith<IllegalStateException> {
            Alarm.buildDevice(context, Meta { "deviceName" put "sensor" })
        }

        val emptyContext = Context("empty")
        assertFailsWith<IllegalStateException> {
            Alarm.buildDevice(emptyContext, Meta {
                "deviceName" put "sensor"
                "propertyName" put "temp"
            })
        }
    }
}
