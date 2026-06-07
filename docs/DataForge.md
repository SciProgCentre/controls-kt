# DataForge Concepts in Controls-kt

`Controls-kt` is built on top of the [DataForge](https://git.sciprog.center/kscience/dataforge-core) framework. Understanding core DataForge concepts is essential for working with `Controls-kt`.

## Context

The `Context` is the central environment for any DataForge-based application. It acts as both a service locator and a lifecycle manager.

- **Service Locator**: All shared components (Plugins) are registered in a `Context`. You can request a plugin from the context, and if it's not present, it will be created and installed.
- **Lifecycle Management**: Every `Context` has an associated `CoroutineScope`. When the context is closed, all jobs running in its scope are cancelled. This ensures that devices and background tasks are properly cleaned up.
- **Hierarchical**: Contexts can have parents, allowing for inherited services and configuration.

Example of creating a context with `DeviceManager`:

```kotlin
val context = Context("MyContext") {
    plugin(DeviceManager)
}
```

## Plugin

A `Plugin` is a modular unit of functionality that can be attached to a `Context`. In `Controls-kt`, most high-level features are implemented as plugins.

- **Modularity**: Plugins allow keeping the core library small while providing optional features like Modbus support, Magix integration, or specialized port implementations.
- **Dependency Management**: Plugins can depend on other plugins. For example, many `Controls-kt` features depend on the `DeviceManager` plugin.
- **Configuration**: Plugins can be configured using `Meta` during installation.

Commonly used plugins in `Controls-kt`:
- `DeviceManager`: Manages the lifecycle and registration of devices.
- `Ports`: Provides a registry for communication ports.
- `Magix`: Integrates devices with the Magix event bus.

## Name

`Name` is a hierarchical identifier used to address devices, properties, and metadata values. It is conceptually similar to a file system path but optimized for performance and type safety.

- **Hierarchical Structure**: A `Name` consists of multiple `NameToken`s. In string representation, these tokens are usually separated by dots (e.g., `room1.sensor2.temperature`).
- **Device Addressing**: In a `DeviceTree`, each device has a `Name` relative to the tree root.
- **Meta Navigation**: `Name` is used to navigate through nested `Meta` structures (DataForge's tree-like data format).
- **Type Safety**: The `Name` class provides methods for manipulating paths safely, such as `+` for concatenation, `cutFirst()`, `first()`, and `parent`.

Example of using `Name`:

```kotlin
val deviceName = "sensors.thermal.main".parseAsName()
val propertyPath = deviceName + "value" // sensors.thermal.main.value

println(deviceName.first()) // sensors
println(deviceName.cutFirst()) // thermal.main
```

## Meta

`Meta` is the universal data format used by DataForge and `Controls-kt`. It is a tree-like structure that can represent scalars, lists, and nested maps.

- **Versatility**: It can be used for configuration, device state, message payloads, and more.
- **Serialization**: `Meta` can be easily converted to and from JSON, CBOR, or other formats.
- **Type Safety**: While `Meta` itself is dynamic, DataForge provides `MetaConverter` to bridge between `Meta` and typed Kotlin objects.
- **Observation**: `MutableMeta` allows observing changes to specific values within the tree.

In `Controls-kt`, property values and action arguments are almost always represented as `Meta`.

<!-- LLM generated code: Explanation of DataForge concepts -->
