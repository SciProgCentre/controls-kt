# Alarm Virtual Device

`Alarm(context, settings)` evaluates numeric inputs against ordered threshold rules.
Create the component first, then connect a `ValueState<Meta>` with `bind(source)`.
The default input name and `"value"` are aliases. Unknown input names and a second binding are rejected.

## Settings and output

`AlarmSetting(lowerThreshold = null, upperThreshold = null, status)` requires at least one
non-null threshold. A rule matches when the input is strictly below its lower threshold or
strictly above its upper threshold. Equality does not trigger it. The last matching rule wins.

- `alarmSettings` is a mutable device property containing the rules. Updating it recalculates the output.
- `state` is a read-only `ValueState<AlarmState>`, also exposed as the `"state"` device property.
  `AlarmState(message, value)` contains both the status and the input value.
- A null or unbound input produces `AlarmState(Alarm.STATUS_UNDEFINED, null)`;
  `STATUS_UNDEFINED` is `"@UNDEFINED"`.
- A non-null input matching no rule produces `Alarm.STATUS_OK` (`"OK"`), including when settings are empty.

## Direct binding

The example runs in a suspending function and uses the application's `context`.

```kotlin
val temperature = MutableValueState<Double?>(22.0)
val alarm = Alarm(context, listOf(
    AlarmSetting(lowerThreshold = 10.0, status = "LOW"),
    AlarmSetting(upperThreshold = 40.0, status = "HIGH"),
    AlarmSetting(upperThreshold = 60.0, status = "CRITICAL"),
))
alarm.bind(temperature.map(MetaConverter.double.nullable()::convert))

temperature.value = 75.0
val result = alarm.state.subscribe().first { it.value == 75.0 }
// AlarmState(message = "CRITICAL", value = 75.0)
```

## Factory configuration

The factory reads `settings` and optional `metadata`, not `deviceName` or `propertyName`.
An absent `settings` node means an empty list. Use the helper to preserve its indexed format:

```kotlin
val parameters = Alarm.buildDeviceMeta(
    listOf(AlarmSetting(upperThreshold = 40.0, status = "HIGH"))
)
val alarm = Alarm.buildDevice(context, parameters)
alarm.bind(temperature.map(MetaConverter.double.nullable()::convert), "value")
```

Each `settings.setting[0]`, `settings.setting[1]`, etc. contains `status` and optional numeric
or explicit-null `lowerThreshold`/`upperThreshold`. At least one threshold must be non-null.
The descriptor describes the editor/catalog shape; the converter and `AlarmSetting` validate each rule.

For configuration-based construction, install `ControlsUtilitiesPlugin`, put
`TemplateDeviceConfiguration(type = "controls.utilities.alarm", parameters = parameters)`
in `ConstructorDeviceConfiguration.components`, and connect its input through `bindings`:

```kotlin
ConstructorBinding(
    sourceDevice = "group.sensor".parseAsName(),
    sourceProperty = "temperature",
    targetDevice = Name.of("alarm"),
    targetInput = "value",
)
```

Paths are relative to the configuration tree receiving the binding.
The short factory name `"alarm"` is accepted only when unambiguous.
