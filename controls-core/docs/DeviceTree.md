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
    device("sensor1", MyDevice) {
        // ... configuration
    }
    node("subnode") {
        device("sensor2", MyDevice)
    }
}
```

## Demos and Tests

- **Tests**: `../src/commonTest/kotlin/space/kscience/controls/spec/SpecTest.kt` contains tests for `DeviceTreeSpec` and tree resolution.
- **Demos**: 
    - `../../demo/thermo`: Uses a hub to manage multiple sensors and a simulator.
    - `../../demo/many-devices`: Demonstrates managing a large number of devices in a tree.

<!-- LLM generated code: Documentation for DeviceHub feature -->
