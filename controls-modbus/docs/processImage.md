# Device Process Image Binding

In Modbus terminology, a **Process Image** is the internal memory representation of a Modbus slave (server) device containing its coils, discrete inputs, input registers, and holding registers.

`controls-modbus` provides the `bindProcessImage` extension function and `DeviceProcessImageBuilder` DSL to bridge any `controls-kt` `Device` to a Modbus process image (powered by `j2mod`).

<!-- LLM generated code: Documentation for Device Process Image Binding -->

---

## Overview

When building a Modbus slave/server with `controls-kt`:
1. You have a `Device` (such as `DeviceConstructor`) containing typed properties and actions.
2. You define a `ModbusRegistryMap` (or keys) mapping device properties/actions to Modbus registers.
3. You call `device.bindProcessImage(...)` to create and populate a Modbus `ProcessImage`.
4. You register the `ProcessImage` with a Modbus slave server (e.g., `ModbusSlaveFactory.createTCPSlave` or `createSerialSlave`).

---

## The `bindProcessImage` Extension

```kotlin
public fun <D : Device> D.bindProcessImage(
    unitId: Int = 0,
    openOnBind: Boolean = true,
    binding: DeviceProcessImageBuilder<D>.() -> Unit,
): ProcessImage
```

- **`unitId`**: Modbus unit identifier / slave address (default is `0`).
- **`openOnBind`**: If `true` (default), launches `device.start()` in the device coroutine scope upon binding.
- **`binding`**: DSL block evaluated within `DeviceProcessImageBuilder<D>` to configure register bindings.

---

## Binding Types in `DeviceProcessImageBuilder`

### 1. Binding Single Registers to Property Specs

For single-word or single-bit values:

```kotlin
// LLM generated code: Binding single registers to DevicePropertySpec
val processImage = device.bindProcessImage(unitId = 1) {
    // Coil (read-write boolean)
    bind(registryMap.powerState, devicSpec.powerProperty)

    // Discrete Input (read-only boolean)
    bind(registryMap.alarmTriggered, devicSpec.alarmProperty)

    // Input Register (read-only 16-bit Short)
    bind(registryMap.rawAdcValue, devicSpec.adcProperty)

    // Holding Register (read-write 16-bit Short)
    bind(registryMap.targetSpeed, devicSpec.speedProperty)
}
```

- **Read-only bindings** (`DiscreteInput`, `InputRegister`) observe device property updates via `useProperty` and update the Modbus process image.
- **Read-write bindings** (`Coil`, `HoldingRegister`) provide **two-way synchronization**:
  - Updates to the device property reflect in the Modbus process image.
  - Modbus master writes trigger observers that update the device property asynchronously via `device.writeAsync`.

---

### 2. Binding Multi-Register Ranges to Properties

For properties encoding multi-word values (e.g., `Double`, `Float`, or custom structures):

#### Using `DevicePropertySpec<T>`

```kotlin
// LLM generated code: Multi-register range binding with DevicePropertySpec
val processImage = device.bindProcessImage(unitId = 1) {
    // Read-only range of input registers (e.g., Double encoded across 4 registers)
    bind(registryMap.temperature, devicSpec.temperatureProperty)

    // Read-write range of holding registers (e.g., Double setpoint)
    bind(registryMap.setpoint, devicSpec.setpointProperty)
}
```

#### Using Dynamic Property Names and Converters/Readers

You can also bind ranges dynamically using property string names and `MetaConverter<T>` or `MetaReader<T>`:

```kotlin
// LLM generated code: Multi-register range binding with property name and MetaConverter
val processImage = device.bindProcessImage(unitId = 1) {
    // Read-only input range
    bind(registryMap.temperature, "temperature", MetaConverter.double)

    // Read-write holding range
    bind(registryMap.setpoint, "setpoint", MetaConverter.double)
}
```

Multi-register holding ranges listen for changes across the registers, buffer and deserialize the payload via `IOFormat<T>`, and invoke `device.writeProperty` / `device.write`.

---

### 3. Binding Actions

Modbus holding registers and coils written by a master can be wired directly to device actions:

```kotlin
// LLM generated code: Binding actions to Modbus register writes
val processImage = device.bindProcessImage(unitId = 1) {
    // Trigger action when coil is toggled
    bindAction(registryMap.resetAlarm) { isSet: Boolean ->
        if (isSet) {
            resetAlarm()
        }
    }

    // Trigger action on holding register write
    bindAction(registryMap.commandCode) { code: Short ->
        executeCommand(code.toInt())
    }

    // Trigger action on holding range write (decoded via IOFormat)
    bindAction(registryMap.targetPosition) { target: Double ->
        moveTo(target)
    }
}
```

---

### 4. Custom Low-Level Bindings

You can customize the underlying `j2mod` process image elements directly using the block overload:

```kotlin
// LLM generated code: Custom low-level j2mod element binding
val processImage = device.bindProcessImage(unitId = 1) {
    bind(registryMap.powerState) { coil: ObservableDigitalOut ->
        coil.addObserver { _, _ ->
            println("Coil written by Modbus master: ${coil.isSet}")
        }
    }
}
```

---

## Full Example: Hosting a Modbus TCP Slave Server

The following complete example illustrates how to define a device, map its registers, create a process image, and serve it via TCP:

```kotlin
// LLM generated code: Complete Modbus TCP Slave Server example
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.modbus.ModbusRegistryMap
import space.kscience.controls.modbus.bindProcessImage
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.io.DoubleIOFormat
import space.kscience.dataforge.meta.MetaConverter

// 1. Define the Registry Map
class ThermoRegistryMap : ModbusRegistryMap() {
    val temperature = input(address = 0, count = 4, reader = DoubleIOFormat, description = "Current temperature")
    val setpoint = register(address = 0, count = 4, format = DoubleIOFormat, description = "Target setpoint")
    val heaterActive = coil(address = 0, description = "Heater switch state")
}

// 2. Define/Instantiate the Device
val context = Context(Global, "thermo-server")
val device = DeviceConstructor(context).apply {
    val tempState = MutableValueState(22.5)
    val setpointState = MutableValueState(25.0)
    val heaterState = MutableValueState(false)

    registerProperty("temperature", MetaConverter.double, state = tempState)
    registerProperty("setpoint", MetaConverter.double, state = setpointState)
    registerProperty("heater", MetaConverter.boolean, state = heaterState)
}

// 3. Bind Device to Process Image
val registryMap = ThermoRegistryMap()
val unitId = 1

val processImage = device.bindProcessImage(unitId = unitId) {
    bind(registryMap.temperature, "temperature", MetaConverter.double)
    bind(registryMap.setpoint, "setpoint", MetaConverter.double)
    bind(registryMap.heaterActive, "heater", MetaConverter.boolean)
}

// 4. Start the Modbus TCP Slave Server
val port = 502
val maxConnections = 5
val slave = ModbusSlaveFactory.createTCPSlave(port, maxConnections)
slave.addProcessImage(unitId, processImage)
slave.open()

println("Modbus TCP Slave listening on port $port for unitId $unitId...")
```
