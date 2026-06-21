# Device Specification (DeviceSpec)

`DeviceSpec` provides a way to define the interface of a device (its properties and actions) separately from its implementation. This allows for type-safe interactions and better separation of concerns.

## Key Concepts

- **Property Specification**: Defines a property name, its data type (via `MetaConverter`), and metadata like description or units.
- **Action Specification**: Defines an action name, its input/output types, and metadata.
- **Device Factory**: Combines a `DeviceSpec` with implementation logic to create a `Device` instance.

## Working with DeviceSpec

Usually, a specification is defined as a Kotlin `object` inheriting from `DeviceSpec`:

```kotlin
object MyDeviceSpec : DeviceSpec() {
    val temperature by doubleProperty {
        description = "Ambient temperature"
    }
    
    val reset by action(MetaConverter.unit, MetaConverter.unit)
}
```

Implementation can then be provided using a `DeviceFactory` or `DeviceBuilder`:

```kotlin
val device = Device(MyDeviceSpec) {
    reader(MyDeviceSpec.temperature) {
        // logic to read temperature from hardware
    }
}
```

## Demos and Tests

- **Tests**: `../src/commonTest/kotlin/space/kscience/controls/spec/SpecTest.kt` contains tests for specification validation and missing element checks.
- **Demos**: 
    - `../../demo/thermo`: Shows complex `DeviceSpec` usage for a thermometer.
    - `../../demo/all-things`: Demonstrates various types of properties (double, string, meta) in a spec.

<!-- LLM generated code: Documentation for DeviceSpec feature -->
