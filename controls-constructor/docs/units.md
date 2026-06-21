# Unit-Aware Modeling

The `units` feature provides a type-safe way to work with physical quantities and units of measurement in simulations and device properties. It prevents errors by ensuring that you don't accidentally add meters to kilograms or divide by the wrong units.

## Core Interfaces

### Amount<U>
Represents a quantity of a certain unit `U`. It is `Comparable`.

### NumericAmount<U>
An inline value class that wraps a `Double` value with a unit type. It provides operator overloads for addition, subtraction, multiplication by a scalar, etc.

## Supported Units

`controls-constructor` defines several common units in `UnitsOfMeasurement.kt`:

- **Distance**: `Meters`, `Millimeters`, `Degrees`, `Radians`.
- **Mass**: `Kilograms`.
- **Force/Torque**: `Newtons`, `NewtonsMeters`.
- **Time**: `Seconds`.
- **Velocity**: `MetersPerSecond`, `DegreesPerSecond`.
- **Inertia**: `KgM2`.

## Unit-Aware Calculations

You can perform calculations while maintaining type safety:

```kotlin
val distance = 10.meters
val time = 2.seconds
val velocity: NumericAmount<MetersPerSecond> = distance / time 
```

## Integration with ValueState and Meta

`units` provides specialized `MetaConverter`s to serialize and deserialize unit-aware values:

```kotlin
val converter = MetaConverter.numeric(Meters)
val meta = converter.convert(10.meters)
// meta = { value: 10.0, units: "m" }
```

You can also map a `ValueState` of `Amount` to `NumericAmount`:
```kotlin
val numericState = state.asNumeric()
```

## Demos and Tests

- **Tests**: `../src/commonTest/kotlin/space/kscience/controls/constructor/CollectAmountAsyncTest.kt`
- **Demos**: `../../demo/constructor`

<!-- LLM generated code: Documentation for Units feature -->
