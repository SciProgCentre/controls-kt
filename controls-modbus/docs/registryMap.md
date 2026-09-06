# Modbus Registry Map

`ModbusRegistryMap` and `ModbusRegistryKey` provide a type-safe declarative way to define and manage Modbus register
layouts in `controls-kt`.

<!-- LLM generated code: Documentation for Modbus Registry Map -->

## Modbus Data Model Overview

Modbus organizes data into four primary tables:

| Registry Type        | Primary Type in controls-kt         | Modbus Code / Size | Access     | Description                                |
|----------------------|-------------------------------------|--------------------|------------|--------------------------------------------|
| **Coil**             | `ModbusRegistryKey.Coil`            | 1-bit              | Read/Write | Discrete output / boolean switch           |
| **Discrete Input**   | `ModbusRegistryKey.DiscreteInput`   | 1-bit              | Read-Only  | Discrete input / boolean sensor            |
| **Input Register**   | `ModbusRegistryKey.InputRegister`   | 16-bit word        | Read-Only  | Analog input / status measurement          |
| **Input Range**      | `ModbusRegistryKey.InputRange<T>`   | N x 16-bit words   | Read-Only  | Multi-register value (Float, Double, etc.) |
| **Holding Register** | `ModbusRegistryKey.HoldingRegister` | 16-bit word        | Read/Write | Analog output / configuration parameter    |
| **Holding Range**    | `ModbusRegistryKey.HoldingRange<T>` | N x 16-bit words   | Read/Write | Multi-register value (Float, Double, etc.) |

---

## ModbusRegistryKey

`ModbusRegistryKey<T>` is a typed sealed interface representing a single register or a contiguous range of registers:

- `address: Int`: Starting address (0-based).
- `count: Int`: Number of 16-bit registers (or 1 for coils and single registers).

### Single-Item Keys

```kotlin
// LLM generated code: ModbusRegistryKey examples
val fanState = ModbusRegistryKey.Coil(address = 0)
val alarmActive = ModbusRegistryKey.DiscreteInput(address = 0)
val rawTemperature = ModbusRegistryKey.InputRegister(address = 10)
val setpoint = ModbusRegistryKey.HoldingRegister(address = 20)
```

### Multi-Register Ranges with IOFormat

For values spanning multiple 16-bit registers (such as 32-bit floats, 64-bit doubles, strings, or custom binary
payloads), use `InputRange<T>` and `HoldingRange<T>`. They use `space.kscience.dataforge.io.IOFormat<T>` for
serialization and deserialization:

```kotlin
// LLM generated code: Multi-register range keys with IOFormat
import space.kscience.dataforge.io.DoubleIOFormat
import space.kscience.dataforge.io.FloatIOFormat

// 64-bit Double encoded across 4 x 16-bit registers
val temperature = ModbusRegistryKey.InputRange(
    address = 100,
    count = 4,
    format = DoubleIOFormat
)

// 32-bit Float encoded across 2 x 16-bit registers
val targetPressure = ModbusRegistryKey.HoldingRange(
    address = 200,
    count = 2,
    format = FloatIOFormat
)
```

---

## Defining a ModbusRegistryMap

Subclass `ModbusRegistryMap` and use the built-in helper functions to declare register definitions:

```kotlin
// LLM generated code: Example ModbusRegistryMap implementation
import space.kscience.controls.modbus.ModbusRegistryMap
import space.kscience.dataforge.io.DoubleIOFormat

class SensorRegistryMap : ModbusRegistryMap() {
    // Coils
    val powerOn = coil(address = 0, description = "Power on/off switch")
    val resetAlarm = coil(address = 1, description = "Reset active alarm")

    // Discrete Inputs
    val alertState = discrete(address = 0, description = "High temperature alert")

    // Input Registers & Ranges
    val rawAdc = input(address = 0, description = "Raw ADC counts")
    val temperature =
        input(address = 10, count = 4, reader = DoubleIOFormat, description = "Calibrated temperature in °C")

    // Holding Registers & Ranges
    val mode = register(address = 0, description = "Operating mode (0=Manual, 1=Auto)")
    val setpoint =
        register(address = 10, count = 4, format = DoubleIOFormat, description = "Temperature setpoint in °C")
}
```

---

## Validation and Consistency Checks

`ModbusRegistryMap` automatically validates memory layout consistency to prevent accidental overlapping registers in the
same section:

```kotlin
// LLM generated code: Registry map validation
val map = SensorRegistryMap()
ModbusRegistryMap.validate(map) // Throws IllegalStateException if overlapping registers exist
```

---

## Printing and JSON Export

You can export or inspect the registry map:

### ASCII Table Printing

```kotlin
map.print()
```

Output:

```text
Coil	0	Power on/off switch
Coil	1	Reset active alarm
Discrete	0	High temperature alert
Input	0	Raw ADC counts
Input	10 - 13	Calibrated temperature in °C
Register	0	Operating mode (0=Manual, 1=Auto)
Register	10 - 13	Temperature setpoint in °C
```

### JSON Export

```kotlin
val json = map.toJson()
```

Produces a `JsonArray` containing descriptor objects with `type`, `address`, optional `count`, and `description`.

---

## Master Operations (Reading and Writing)

Extension functions on `AbstractModbusMaster` allow type-safe reading and writing using `ModbusRegistryKey`:

```kotlin
// LLM generated code: Reading and writing via Master
val master: AbstractModbusMaster = ...
val unitId = 1
val map = SensorRegistryMap()

// Reading
val isPowerOn: Boolean = master.read(unitId, map.powerOn)
val temp: Double = master.read(unitId, map.temperature)
val currentMode: Short = master.read(unitId, map.mode)

// Writing
master.write(unitId, map.powerOn, true)
master.write(unitId, map.mode, 1.toShort())
master.write(unitId, map.setpoint, 23.5)
```
