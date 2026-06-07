# Device Manager

`DeviceManager` is a DataForge `Plugin` that integrates `Controls-kt` with the DataForge Dependency Injection (DI) system. It manages the lifecycle of devices and provides a way to register and lookup devices within a `Context`.

## Key Features

- **Lifecycle Management**: Automatically starts and stops devices when the context is started or stopped.
- **DI Integration**: Allows installing devices into a context using factories and metadata.
- **Tree Structure**: `DeviceManager` itself acts as a `DeviceTree`, allowing hierarchical organization of managed devices.

## Working with DeviceManager

You can install a device into the context:
```kotlin
val context = Context {
    plugin(DeviceManager)
}

val device = context.install("myDevice", MyDeviceFactory)
```

You can also use a property delegate for lazy installation:
```kotlin
val device by deviceManager.installing(MyDeviceFactory)
```

## Demos and Tests

- **Demos**: 
    - `../../demo/thermo`: Uses `DeviceManager` to set up the device environment from configuration.
    - `../../demo/data-platform-demo`: Demonstrates using `DeviceManager` to install devices from configuration.
- **Tests**: Currently, `DeviceManager` is primarily tested through demos. See [TODO.md](TODO.md) for planned unit tests.

<!-- LLM generated code: Documentation for DeviceManager feature -->
