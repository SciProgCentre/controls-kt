# Device Specification (DeviceSpec)

`DeviceSpec` provides a way to define the interface of a device (its properties and actions) separately from its implementation. This allows for type-safe interactions and better separation of concerns.

## Key Concepts

- **Property Specification**: Defines a property name, its data type (via `MetaConverter`), and metadata like description or units.
- **Action Specification**: Defines an action name, its input/output types, and metadata.
- **Device Factory**: Combines a `DeviceSpec` with implementation logic to create a `Device` instance.

## Working with DeviceSpec

Usually, a specification is defined as a Kotlin `object` inheriting from `AbstractDeviceSpec` (or implementing `DeviceSpec`):

```kotlin
object MyDeviceSpec : AbstractDeviceSpec() {
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

## Compile-Time Safety with `SpecificDevice`

While a standard `Device` exposes properties and actions dynamically by name or via generic calls, `SpecificDevice<S : DeviceSpec>` provides compile-time type safety. It is an inline value class wrapping a `Device` that guarantees compliance with specification `S`.

### Creating a `SpecificDevice`

A `SpecificDevice` can be created in several ways:

1. **Verifying an existing `Device`**:
   The `verifiedWith(spec)` extension checks that all properties and actions declared in `spec` are present in the target device (via `checkMissingElements`). If verification succeeds, it returns `SpecificDevice<S>`:
   ```kotlin
   val specificDevice: SpecificDevice<MyDeviceSpec> = device.verifiedWith(MyDeviceSpec)
   ```
   If any required element is missing or descriptor mismatch occurs, an error is thrown.

2. **Using the `SpecificDevice` builder**:
   You can construct and verify a device in a single step:
   ```kotlin
   val specificDevice = SpecificDevice(context, MyDeviceSpec) {
       reader(MyDeviceSpec.temperature) {
           readTemperatureSensor()
       }
   }
   ```

3. **Using `DeviceWithStateBuilder.buildSpecific`**:
   When working with stateful device builders (`DeviceWithStateBuilder` / `DeviceWithStateFactory`):
   ```kotlin
   val specificDevice: SpecificDevice<MyStateBuilder> = 
       myDeviceWithStateBuilder.buildSpecific(context, meta)
   ```

### Specification Extensions

The primary reason to use `SpecificDevice<S>` is to attach strongly typed, domain-specific extension functions and properties to devices that adhere to specification `S`:

```kotlin
// Strongly typed extension property
val SpecificDevice<MyDeviceSpec>.temperature: Double
    get() = read(MyDeviceSpec.temperature)

// Strongly typed extension method
suspend fun SpecificDevice<MyDeviceSpec>.resetToDefault() {
    execute(MyDeviceSpec.reset)
}
```

These extensions can only be called on instances of `SpecificDevice<MyDeviceSpec>`, ensuring compile-time safety and providing IDE autocompletion for device-specific operations.

## Demos and Tests

- **Tests**: `../src/commonTest/kotlin/space/kscience/controls/spec/SpecTest.kt` contains tests for specification validation and missing element checks.
- **Demos**: 
    - `../../demo/thermo`: Shows complex `DeviceSpec` usage for a thermometer.
    - `../../demo/all-things`: Demonstrates various types of properties (double, string, meta) in a spec.

<!-- LLM generated code: Documentation for DeviceSpec feature -->
