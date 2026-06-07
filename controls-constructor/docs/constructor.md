# Device and Group Construction

The `constructor` feature provides a high-level DSL for hierarchical composition of devices and simulations. It leverages the `Constructor` and `MutableConstructor` interfaces to manage elements and their connections.

## DeviceConstructor

`DeviceConstructor` is the primary class for building complex devices. It extends `DeviceTree` and `MutableConstructor`, allowing it to:
- Manage internal states (`ValueState`).
- Define reactive connections between states.
- Expose states as device properties.
- Install sub-devices.

### Defining Properties
You can easily expose internal states as device properties using delegates:

- **property**: Exposes a `ValueState` as a read-only property.
- **mutableProperty**: Exposes a `MutableValueState` as a read-write property.
- **virtualProperty**: Creates a new `MutableValueState` and exposes it as a property (useful for simulation parameters).

```kotlin
class MyDevice(context: Context) : DeviceConstructor(context) {
    // A virtual property for simulation
    val targetPosition by virtualProperty(MetaConverter.double, 0.0)
    
    // A property backed by a reader function
    val actualPosition by property(MetaConverter.double, reader = { readFromHardware() }, readInterval = 100.milliseconds)
}
```

### Installing Sub-devices
Devices can be composed hierarchically:

```kotlin
class Robot(context: Context) : DeviceConstructor(context) {
    val leftMotor by device(Motor.spec)
    val rightMotor by device(Motor.spec)
}
```

## MutableConstructor

The `MutableConstructor` interface provides methods for wiring states and handling events:

- **bindState**: Synchronize two states.
- **onNext / onChange**: Execute a block when a state changes.
- **onTimer**: Execute a block periodically, synchronized with the `ClockManager`.

## DSL Elements

The construction process produces a graph of `ConstructorElement`s:
- **PropertyConstructorElement**: Links a device property to a state.
- **StateConstructorElement**: Represents a standalone state.
- **ConnectionConstructorElement**: Represents a binding or transformation between states.
- **ModelConstructorElement**: Represents a simulation model.

## Demos and Tests

- **Tests**: `../src/commonTest/kotlin/space/kscience/controls/constructor/DeviceGroupTest.kt`
- **Demos**: `../../demo/constructor`

<!-- LLM generated code: Documentation for Constructor feature -->
