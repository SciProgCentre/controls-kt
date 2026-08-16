# Alarm Virtual Device

The `Alarm` class is a specialized virtual device (`DeviceConstructor`) in the `controls-utilities` module designed to monitor an observable numeric state (`ValueState<Double?>`) and produce multi-stage alarm statuses based on dynamic threshold settings.

## Core Concepts

### AlarmSetting

`AlarmSetting` is a `@Serializable` data class that configures a threshold rule and the associated status string:

```kotlin
@Serializable
public data class AlarmSetting(
    val lowerThreshold: Double?,
    val upperThreshold: Double?,
    val status: String
)
```

- **`lowerThreshold`**: Lower numeric boundary (`Double?`). If the monitored value is strictly less than this threshold (`value < lowerThreshold`), the rule matches. Can be `null` if no lower bound is set.
- **`upperThreshold`**: Upper numeric boundary (`Double?`). If the monitored value is strictly greater than this threshold (`value > upperThreshold`), the rule matches. Can be `null` if no upper bound is set.
- **`status`**: The status string returned when the threshold condition is violated (e.g. `"WARNING"`, `"CRITICAL"`, `"TOO_COLD"`, `"TOO_HOT"`).
- **Validation**: At least one of `lowerThreshold` or `upperThreshold` must be defined (non-null); otherwise, an `IllegalArgumentException` is thrown.

### Alarm Device

`Alarm` extends `DeviceConstructor` and monitors a given `ValueState<Double?>`:

```kotlin
public class Alarm(
    context: Context,
    private val value: ValueState<Double?>
) : DeviceConstructor(context)
```

#### States and Properties

- **`alarmSettings`**: `MutableValueState<List<AlarmSetting>>` exposed as a device property. It holds the active list of alarm threshold rules. Modifying this property dynamically recalculates the alarm status.
- **`state`**: `ValueState<String>` registered as the read-only device property `"state"`. It provides the current evaluated alarm status.

#### Status Evaluation Logic

1. **Undefined Value**: If the input value is `null`, the evaluated status is always `Alarm.STATUS_UNDEFINED` (`"UNDEFINED"`).
2. **Normal State**: If the input value does not violate any thresholds (or if `alarmSettings` is empty), the evaluated status is `Alarm.STATUS_OK` (`"OK"`).
3. **Strict Comparison**: Threshold violations are strictly evaluated (`value < lowerThreshold` or `value > upperThreshold`). Exact boundary matches (`value == threshold`) do not trigger a violation.
4. **Rule Precedence ("Last Wins")**: Settings in `alarmSettings` are evaluated in list order. If multiple threshold rules match simultaneously (e.g., warning and critical limits), **the last violated rule in the list takes precedence**.

## Usage Examples

### 1. Direct Instantiation

You can create an `Alarm` device by passing a context and any `ValueState<Double?>`:

```kotlin
val context = Context("alarmContext")
val temperatureState = MutableValueState<Double?>(22.0)

val alarm = Alarm(context, temperatureState)

// Configure multi-stage alarm thresholds
alarm.alarmSettings.value = listOf(
    AlarmSetting(lowerThreshold = 10.0, upperThreshold = null, status = "LOW_WARNING"),
    AlarmSetting(lowerThreshold = 0.0, upperThreshold = null, status = "LOW_CRITICAL"),
    AlarmSetting(lowerThreshold = null, upperThreshold = 40.0, status = "HIGH_WARNING"),
    AlarmSetting(lowerThreshold = null, upperThreshold = 60.0, status = "HIGH_CRITICAL")
)

// Read current status
println(alarm.state.value) // Prints: "OK"

// Update temperature
temperatureState.value = 45.0
println(alarm.state.value) // Prints: "HIGH_WARNING"

temperatureState.value = 75.0
println(alarm.state.value) // Prints: "HIGH_CRITICAL" (last matching rule wins)

temperatureState.value = null
println(alarm.state.value) // Prints: "UNDEFINED"
```

### 2. DeviceFactory and DeviceManager Integration

`Alarm.Companion` implements `DeviceFactory` to allow constructing an `Alarm` device from DataForge `Meta` configuration and an existing device in `DeviceManager`:

```kotlin
val context = Context("mainContext") {
    plugin(DeviceManager)
}
val deviceManager = context.request(DeviceManager)

// Install source device
val sensor = deviceManager.install("temperatureSensor", MySensorDevice(context))

// Create alarm device via Meta configuration
val alarmMeta = Meta {
    "deviceName" put "temperatureSensor" // Supports hierarchical names like "group.sensor"
    "propertyName" put "temperature"
}

val alarmDevice = Alarm.buildDevice(context, alarmMeta)
```

#### Configuration Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `deviceName` | `String` / `Name` | Yes | Name of the source device in `DeviceManager` to observe (parsed via `parseAsName()`). |
| `propertyName` | `String` | Yes | Name of the numeric property on the source device. |

## Tests

- Comprehensive unit tests covering threshold validations, boundary evaluation, multi-stage priority, dynamic updates, and factory instantiation are located in `controls-utilities/src/commonTest/kotlin/space/kscience/controls/utilities/AlarmTest.kt`.

<!-- LLM generated code: Documentation for Alarm device in controls-utilities -->
