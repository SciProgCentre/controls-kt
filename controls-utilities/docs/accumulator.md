# Accumulator Virtual Device

`Accumulator(context, window, coroutineScope = context)` produces a rolling sum of numeric
samples. It does not multiply by elapsed time: it is not a physical integrator or a flow
totalizer. Sampling frequency therefore affects the sum.

Create the component first, then connect a `ValueState<Meta>` with `bind(source)`.
The default input name and `"value"` are aliases; unknown names and repeated binding are rejected.
The read-only `state: ValueState<Double>` is exposed as the `"state"` device property.

## Time and window semantics

1. Samples older than the current result are ignored. Equal timestamps are accepted.
2. Accepted samples remove entries older than `sample.time - window` and set the result
   timestamp to `sample.time`. The lower boundary is included.
3. Accepted null samples add nothing, and an empty window sums to `0.0`. The window advances
   only on incoming samples; there is no expiry timer.

By default, `Accumulator` and direct `accumulate` start at `0.0`.
The current source sample is processed by the subscription, including a sample with
`Instant.DISTANT_PAST`. An explicit `startingValue` for `accumulate` is a separate
sample in the window.

A conflating source can skip intermediate updates.

## Direct binding

The example runs in a suspending function and uses the application's `context`.

```kotlin
val sensor = MutableValueState<Double?>(10.0)
val accumulator = Accumulator(context, 5.seconds)
accumulator.bind(sensor.map(MetaConverter.double.nullable()::convert))

val sum = accumulator.state.subscribe().first { it == 10.0 }
println(sum)
```

## Factory configuration

The factory requires `window`: numeric seconds or a string accepted by `Duration.parse`,
such as `"5s"` or `"PT5S"`.

```kotlin
val parameters = Meta { "window" put "5s" }
val accumulator = Accumulator.buildDevice(context, parameters)
accumulator.bind(sensor.map(MetaConverter.double.nullable()::convert))
```

For configuration-based construction, install `ControlsUtilitiesPlugin`, add
`TemplateDeviceConfiguration(type = "controls.utilities.accumulator", parameters = parameters)`
to `ConstructorDeviceConfiguration.components`, and connect a source property through:

```kotlin
ConstructorBinding(
    sourceDevice = "group.sensor".parseAsName(),
    sourceProperty = "flowRate",
    targetDevice = Name.of("accumulator"),
    targetInput = "value",
)
```

The short factory name `"accumulator"` is accepted only when unambiguous.
