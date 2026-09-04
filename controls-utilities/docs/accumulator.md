# Accumulator Virtual Device

`Accumulator(context, window, coroutineScope = context)` produces a rolling sum of numeric
samples. It does not multiply by elapsed time: it is not a physical integrator or a flow
totalizer. Sampling frequency therefore affects the sum.

Create the component first, then connect a `ValueState<Meta>` with `bind(source)`.
The default input name and `"value"` are aliases; unknown names and repeated binding are rejected.
The read-only `state: ValueState<Double>` is exposed as the `"state"` device property.

## Time and window semantics

1. A non-null sample is added only when its timestamp is later than the current result's timestamp.
2. Every incoming sample removes entries older than `sample.time - window` and gives the result
   its timestamp, including null and out-of-order samples. The lower boundary is included.
3. Null adds nothing, and an empty window sums to `0.0`. The window advances only on incoming
   samples; there is no expiry timer.

`Accumulator` starts at `0.0`. A bound `ValueState(25.0)` uses `Instant.DISTANT_PAST` and adds
nothing; use timestamped sources such as `MutableValueState`. Direct `integrate` instead uses
the source's value at construction as its initial sum.

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
