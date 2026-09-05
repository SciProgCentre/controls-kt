# Device Hub (Device Tree)

> **Note**: In the latest versions of `Controls-kt`, `DeviceHub` has been refactored and renamed to `DeviceTree`.

A `DeviceTree` (formerly `DeviceHub`) is a grouping of devices into a local tree-like structure. It allows for hierarchical naming and routing of messages to specific devices within the tree.

## Structure

A `DeviceTree` consists of:
- An optional **root device**.
- A map of **children**, where each child is itself a `DeviceTree` associated with a string name.

This structure allows creating complex hierarchies like `lab.room1.table.sensor`.

## Working with DeviceTree

You can resolve a device by its hierarchical name:
```kotlin
val sensor = deviceTree.resolveDevice("room1.table.sensor".parseAsName())
```

You can also perform operations on devices within the tree:
```kotlin
deviceTree.readProperty("room1.sensor".parseAsName(), "value")
```

## Building a Tree

The recommended way to build a `DeviceTree` is using the `DeviceTreeBuilder` DSL:
```kotlin
val hub = DeviceTree {
    device("sensor1") {
        // ... configuration
    }
    tree("subnode") {
        device("sensor2") {
            // ... configuration
        }
    }
}
```

## Tree Specifications and `SpecificDeviceTree`

Just as `DeviceSpec` specifies the contract for a single device, `DeviceTreeSpec` describes the expected topology of a whole device tree, specifying the root device contract (`deviceSpec`) and child tree contracts (`childrenSpecs`):

```kotlin
interface DeviceTreeSpec {
    val deviceSpec: DeviceSpec? get() = null
    val childrenSpecs: Map<String, DeviceTreeSpec>
}
```

### Compile-Time Safety with `SpecificDeviceTree`

`SpecificDeviceTree<S : DeviceTreeSpec>` is an inline value class wrapping a `DeviceTree` (`@JvmInline value class SpecificDeviceTree<S : DeviceTreeSpec>`) that guarantees adherence to specification `S` at compile time.

### Creating and Verifying a `SpecificDeviceTree`

1. **Defining a `DeviceTreeSpec`**:
   Specifications can be defined as Kotlin objects or created with helper functions:
   ```kotlin
   object LabTreeSpec : DeviceTreeSpec {
       val mainThermometer = MyDeviceSpec
       
       override val childrenSpecs = mapOf(
           "sensor" to DeviceTreeSpec(device = mainThermometer)
       )
   }
   ```

2. **Verifying an existing `DeviceTree`**:
   The `verifiedWith(spec)` extension method verifies that the tree structure and all associated device specifications match `spec` recursively (using `checkMissingElements`). If valid, it returns `SpecificDeviceTree<S>`:
   ```kotlin
   val specificTree: SpecificDeviceTree<LabTreeSpec> = deviceTree.verifiedWith(LabTreeSpec)
   ```
   If elements are missing in the tree hierarchy, an error is thrown listing all missing descriptors.

### Adding Tree Extensions

With `SpecificDeviceTree<S>`, you can write type-safe navigation and domain-specific extensions directly on the tree structure:

```kotlin
// Type-safe accessor for a known child device
val SpecificDeviceTree<LabTreeSpec>.sensor: SpecificDevice<MyDeviceSpec>
    get() = resolveDevice("sensor".parseAsName())!!.verifiedWith(MyDeviceSpec)

// Domain-level operation on the verified tree
suspend fun SpecificDeviceTree<LabTreeSpec>.readLabTemperature(): Double =
    sensor.temperature
```

## Demos and Tests

- **Tests**: `../src/commonTest/kotlin/space/kscience/controls/spec/SpecTest.kt` contains tests for `DeviceTreeSpec` and tree resolution.
- **Demos**: 
    - `../../demo/thermo`: Uses a hub to manage multiple sensors and a simulator.
    - `../../demo/many-devices`: Demonstrates managing a large number of devices in a tree.

<!-- LLM generated code: Documentation for DeviceHub feature -->
