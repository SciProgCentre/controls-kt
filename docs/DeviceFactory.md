# Device Factory and DeviceWithStateBuilder

`DeviceWithStateFactory` is one of ways to create a `Device` in `Controls-kt`. It combines the `DeviceSpec` (declaration of properties and actions) with the implementation logic.

## The concept of State

A `DeviceWithStateFactory<S>` manages an internal state of type `S` for the whole device. This state can be anything: a simple data class, a connection to a hardware port, or a complex object managing multiple resources.

The factory is responsible for:
1. Creating the state (`createState`).
2. Registering properties and actions that operate on this state.
3. Building the `Device` instance.

## Creating a DeviceWithStateFactory

To create a factory, you should inherit from `DeviceWithStateFactory<S>` and implement `createState`:

```kotlin
class MyDeviceState(var value: Double = 0.0)

object MyDevice : DeviceWithStateFactory<MyDeviceState>() {
    // Register properties
    val value by mutableDoubleProperty(
        read = { value },
        write = { value = it }
    )

    override suspend fun createState(): MyDeviceState = MyDeviceState()
}
```

## Property Registration

Inside a `DeviceWithStateFactory` (or its parent class `DeviceWithStateBuilder`), you can use several delegate functions to register properties:

- `property(converter, ...)`: A generic read-only property.
- `mutableProperty(converter, ...)`: A generic read-write property.
- `doubleProperty(...)`, `intProperty(...)`, `stringProperty(...)`, `booleanProperty(...)`: Specialized variants for common types.
- `mutableDoubleProperty(...)`, etc.: Mutable specialized variants.

These delegates take `read` and `write` blocks where `this` refers to the state `S`.

Example:
```kotlin
val position by doubleProperty(
    read = { getPositionFromHardware() }
)
```

## Action Registration

Actions can be registered using the `action` delegate:

```kotlin
val reset by action(MetaConverter.unit, MetaConverter.unit) {
    resetHardware()
}
```

The action block also has access to the state `S`.

## Initialization and Finalization

The `createState` method is where you initialize your state. You can also use it to start recurring tasks or background loops using `device` context.

```kotlin
context(device: DeviceBase)
override suspend fun createState(): MyDeviceState = MyDeviceState().also {
    device.launch {
        // initialization logic
    }
}
```

To clean up resources, override `destroyState`:

```kotlin
override fun destroyState(state: MyDeviceState) {
    state.close()
}
```

## Building the Device

Once you have a factory, you can create a device instance using the `build` method:

```kotlin
val device = MyDevice.build(context, meta)
```
