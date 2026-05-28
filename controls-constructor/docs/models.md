# Simulation Models

The `models` feature provides a library of reusable physical and logical components for building simulations. These models are typically implemented by extending `ModelConstructor` and use `ValueState` for inputs and outputs.

## Common Models

`controls-constructor` includes several pre-built models:

### 1. PID Regulator
A classic Proportional-Integral-Derivative controller. It takes a `target` state and a `measurement` state and produces a `control` signal.

- **File**: `space.kscience.controls.constructor.models.PidRegulator`

### 2. Inertia
Models inertial movement, either linear (mass) or circular (moment of inertia). It computes position and velocity based on the applied force/torque.

- **File**: `space.kscience.controls.constructor.models.Inertia`
- **Supported types**: Linear (Meters, MetersPerSecond) and Circular (Degrees, DegreesPerSecond).

### 3. Reducer
Models a mechanical reducer with a specific ratio.

- **File**: `space.kscience.controls.constructor.models.Reducer`

### 4. Leadscrew
Models a leadscrew that converts circular motion into linear motion.

- **File**: `space.kscience.controls.constructor.models.Leadscrew`

### 5. Material Point
A simple model of a material point with mass.

- **File**: `space.kscience.controls.constructor.models.MaterialPoint`

## Building Custom Models

To build a custom model, extend `ModelConstructor`:

```kotlin
class MyModel(context: Context) : ModelConstructor(context) {
    val input = stateOf(0.0)
    val output = stateOf(0.0)

    init {
        // Models can use timers to update their state
        onTimer(10.milliseconds) {
            output.value = input.value * 2
        }
    }
}
```

## Demos and Tests

- **Tests**: `../src/commonTest/kotlin/space/kscience/controls/constructor/models/` (Currently missing, see [TODO.md](TODO.md))
- **Demos**: `../../demo/constructor`

<!-- LLM generated code: Documentation for Models feature -->
