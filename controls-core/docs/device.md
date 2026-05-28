# Device

The `Device` interface is the core component of the `Controls-kt` library. It represents a hardware or software device that has properties, can perform actions, and communicates via messages.

## Key Features

- **Asynchronous Properties**: Properties can be read and written asynchronously.
- **Subscription Model**: You can observe property changes through a `Flow` of `DeviceMessage`.
- **Lifecycle Management**: Devices have a lifecycle (STARTING, STARTED, STOPPING, STOPPED).
- **Type Safety**: When used with `DeviceSpec`, it provides a type-safe way to interact with the device.

## How to work with it

A `Device` instance allows you to:
- `readProperty(name)`: Get the current value of a property.
- `writeProperty(name, value)`: Set a new value for a property.
- `execute(action, argument)`: Run a command on the device.
- `messageFlow`: Subscribe to all messages emitted by the device.

Example of reading a property:
```kotlin
val value = device.readProperty("temperature")
```

Example of subscribing to changes:
```kotlin
device.messageFlow.collect { message ->
    if (message is PropertyChangedMessage) {
        println("Property ${message.property} changed to ${message.value}")
    }
}
```

## Demos and Tests

- **Tests**: `../src/commonTest/kotlin/space/kscience/controls/spec/SpecTest.kt` contains tests for device interaction through specifications.
- **Demos**: 
    - `../../demo/thermo`: A complex demo with a simulator and real device integration.
    - `../../demo/motors`: Demo for controlling motor devices.
    - `../../demo/all-things`: A comprehensive demo showing various device features.

<!-- LLM generated code: Documentation for Device feature -->
