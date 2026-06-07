# Simulated Devices

The `simulatedDevices` feature provides a set of pre-defined device implementations that are ready to be used in constructions and simulations. These devices wrap simulation models into the standard `Device` API.

## Available Devices

### 1. Drive
A simple drive that provides a force or torque.
- **Properties**: `force` (NewtonsMeters).

### 2. EncoderDevice
Represents an encoder that measures position.
- **Properties**: `position`.

### 3. LimitSwitch
A device that detects if a moving part reaches a certain boundary.
- **Properties**: `locked` (Boolean).
- **Utility**: Can be created from a `position` state, a `limit` value, and a `boundary` direction (UP/DOWN).

### 4. StepDrive
A simulated stepper motor drive.

### 5. LinearDrive
A drive for linear motion.

## Using Simulated Devices

Simulated devices can be installed in a `DeviceConstructor` just like any other device:

```kotlin
class MySimulation(context: Context) : DeviceConstructor(context) {
    val motor by device(Drive(context))
    val limitSwitch by device(LimitSwitch(context, 100.meters, Direction.UP, motorPositionState))
}
```

## Demos and Tests

- **Demos**: `../../demo/constructor`

<!-- LLM generated code: Documentation for Simulated Devices feature -->
