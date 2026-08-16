# Accumulator Virtual Device

The `Accumulator` class is a specialized virtual device (`DeviceConstructor`) in the `controls-utilities` module designed to integrate values from an observable numeric state (`ValueState<Double?>`) over a sliding time window. Null values are ignored during integration.

## Core Concepts

### Accumulator Device

`Accumulator` extends `DeviceConstructor` and integrates a given `ValueState<Double?>` over a time duration `window`:

```kotlin
public class Accumulator(
    context: Context,
    private val value: ValueState<Double?>,
    public val window: Duration,
) : DeviceConstructor(context)
```

#### States and Properties

- **`window`**: `Duration` specifying the sliding time window over which historical values are accumulated.
- **`state`**: `ValueState<Double>` registered as the read-only device property `"state"`. It provides the current sum of all non-null values observed within the time interval `[currentTime - window, currentTime]`.

#### Integration Logic

1. **Window-based Summation**: Values received within the duration window `(currentTime - window, currentTime]` are retained and summed.
2. **Ignoring Nulls**: If a `null` value is emitted by the source `ValueState<Double?>`, it is not added to the accumulation history. However, expired samples older than the window are still pruned, and the updated sum is emitted.
3. **Empty/Expired State**: When all historical values are outside the time window or all past values were `null`, `state.value` evaluates to `0.0`.

## Extension Function

An extension function `ValueState<Double?>.integrate(window: Duration, scope: CoroutineScope): ValueState<Double>` is provided to compute the integral directly without wrapping into a device:

```kotlin
val integratedState: ValueState<Double> = sourceState.integrate(10.seconds, coroutineScope)
```

## Usage Examples

### 1. Direct Instantiation

```kotlin
val context = Context("accumulatorContext")
val sensorState = MutableValueState<Double?>(10.0)

val accumulator = Accumulator(context, sensorState, 5.seconds)

println(accumulator.state.value) // 10.0

sensorState.value = 20.0
println(accumulator.state.value) // 30.0 (sum within 5 seconds)

sensorState.value = null
println(accumulator.state.value) // 30.0 (null is ignored)
```

### 2. DeviceFactory and DeviceManager Integration

`Accumulator.Companion` implements `DeviceFactory` to allow creating an `Accumulator` device from DataForge `Meta` configuration and an existing device registered in `DeviceManager`:

```kotlin
val context = Context("mainContext") {
    plugin(DeviceManager)
    plugin(ControlsUtilitiesPlugin)
}
val deviceManager = context.request(DeviceManager)

// Install source device
val sensor = deviceManager.install("flowMeter", FlowMeterDevice(context))

// Create accumulator device via Meta configuration
val accumulatorMeta = Meta {
    "deviceName" put "flowMeter"
    "propertyName" put "flowRate"
    "window" put "10s" // Duration string or numeric seconds
}

val accumulatorDevice = Accumulator.buildDevice(context, accumulatorMeta)
```

#### Configuration Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `deviceName` | `String` / `Name` | Yes | Name of the source device in `DeviceManager` to observe (parsed via `parseAsName()`). |
| `propertyName` | `String` | Yes | Name of the numeric property on the source device. |
| `window` | `Duration` / `String` / `Number` | Yes | The sliding time window duration (e.g. `"10s"`, `10.0`, or Duration Meta). |

## Tests

- Unit tests covering window accumulation, expiry, null value handling, property registration, and factory creation are located in `controls-utilities/src/commonTest/kotlin/space/kscience/controls/utilities/AccumulatorTest.kt`.

<!-- LLM generated code: Documentation for Accumulator device in controls-utilities -->
