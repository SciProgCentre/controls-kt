# Device Configuration JSON Specification

`ConstructorDeviceConfiguration` provides a declarative, serializable specification for constructing hierarchical device
trees, reactive properties, template components, and inter-device bindings.

This document describes the JSON schema used to serialize and deserialize `ConstructorDeviceConfiguration`. It serves as
a reference for authoring device configurations manually, integrating with external systems, or generating
configurations programmatically.

## Top-Level Structure

A device configuration JSON document represents a single `ConstructorDeviceConfiguration` root node:

```json
{
  "properties": {},
  "devices": {},
  "components": {},
  "bindings": [],
  "parameters": {},
  "metadata": {}
}
```

### Fields

| Field        | Type     | Default | Description                                                                                                      |
|--------------|----------|---------|------------------------------------------------------------------------------------------------------------------|
| `properties` | `object` | `{}`    | Map of property names to their reactive `ValueStateConfiguration` definitions.                                   |
| `devices`    | `object` | `{}`    | Map of sub-device names to nested `ConstructorDeviceConfiguration` objects.                                      |
| `components` | `object` | `{}`    | Map of component names to `TemplateDeviceConfiguration` virtual/template devices.                                |
| `bindings`   | `array`  | `[]`    | List of `ConstructorBinding` objects defining reactive data connections between properties and component inputs. |
| `parameters` | `object` | `{}`    | Configuration parameters passed to the `DeviceConstructor` instance.                                             |
| `metadata`   | `object` | `{}`    | Arbitrary user or application metadata.                                                                          |

---

## Properties (`ValueStateConfiguration`)

The `properties` object maps each exposed property name to a `ValueStateConfiguration` describing how its reactive state
is produced.

```json
"properties": {
  "temperature": {
    "type": "tagTable",
    "parameters": {
      "tag": "sensors.temp1"
    },
    "metadata": {
      "unit": "celsius"
    }
  }
}
```

### Fields

| Field        | Type     | Default      | Description                                                                                                                                                                                |
|--------------|----------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `type`       | `string` | **required** | Factory type identifier used to construct the `ValueState` (e.g., `"deviceProperty"`, `"math"`, `"expression"`, `"tagTable"`). Short names and fully-qualified plugin names are supported. |
| `parameters` | `object` | `{}`         | Factory-specific parameters (`Meta`).                                                                                                                                                      |
| `metadata`   | `object` | `null`       | Optional metadata associated with the property (descriptors, UI hints, units).                                                                                                             |

### Standard Value State Factories

#### 1. Device Property (`deviceProperty` / `controls.constructor.deviceProperty`)

Binds to an existing property of another device.

```json
{
  "type": "deviceProperty",
  "parameters": {
    "deviceName": "sensors.tempSensor",
    "propertyName": "temperature",
    "defaultValue": 0.0
  }
}
```

* `deviceName` (`string`, required): Name or path of the target device.
* `propertyName` (`string`, required): Name of the property on the target device.
* `defaultValue` (`any`, optional): Initial value used before the target property emits.

#### 2. Math Formula (`math` / `controls-expression.math`)

Evaluates a mathematical expression formula string reactively using KMath expressions.

```json
{
  "type": "math",
  "parameters": {
    "expression": "2.0 * x + y",
    "arguments": {
      "x": {
        "type": "tagTable",
        "parameters": {
          "tag": "sensor.x"
        }
      },
      "y": {
        "type": "math",
        "parameters": {
          "expression": "10.0"
        }
      }
    }
  }
}
```

##### Parameters

* `expression` (`string`, required): Mathematical expression string parsed using KMath AST syntax.
* `arguments` (`object`, optional): Map of variable names to nested `ValueStateConfiguration` objects providing input values.

##### Available Operations and Functions

| Category | Operation / Function | Syntax Example | Description |
|---|---|---|---|
| **Binary Arithmetic** | `+` | `a + b` | Addition |
| | `-` | `a - b` | Subtraction |
| | `*` | `a * b` | Multiplication |
| | `/` | `a / b` | Division |
| | `^` or `pow` | `x ^ 2` or `pow(x, 2)` | Power / Exponentiation |
| **Unary Math** | `-` | `-x` | Arithmetic negation |
| | `sin` | `sin(x)` | Trigonometric sine |
| | `cos` | `cos(x)` | Trigonometric cosine |
| | `asin` | `asin(x)` | Trigonometric inverse sine (arcsine) |
| | `acos` | `acos(x)` | Trigonometric inverse cosine (arccosine) |
| | `atan` | `atan(x)` | Trigonometric inverse tangent (arctangent) |
| | `exp` | `exp(x)` | Exponential function ($e^x$) |
| | `ln` | `ln(x)` | Natural logarithm ($\ln x$) |
| | `sqrt` | `sqrt(x)` | Square root ($\sqrt{x}$) |
| **Time-Series & Calculus** | `diff` | `diff(x)` | Time derivative (rate of change in units/second: $\frac{\Delta x}{\Delta t}$) |
| | `integrateMinute` | `integrateMinute(x)` | Rolling trapezoid time integral over a 1-minute window ($60\text{ s}$) |
| | `integrateHour` | `integrateHour(x)` | Rolling trapezoid time integral over a 1-hour window ($3600\text{ s}$) |
| | `integrateDay` | `integrateDay(x)` | Rolling trapezoid time integral over a 1-day window ($86400\text{ s}$) |
| | `integrate` | `integrate(arg: x, windows: 30)` | Rolling trapezoid time integral of `arg` over a custom duration window `windows` (in seconds) |

#### 3. Structured Expression (`expression` / `controls-expression.expression`)

Evaluates a structured Abstract Syntax Tree (`ValueStateExpression`) represented directly in JSON.

```json
{
  "type": "expression",
  "parameters": {
    "expression": {
      "type": "binary",
      "operation": "+",
      "left": {
        "type": "state",
        "valueStateType": "deviceProperty",
        "parameters": {
          "deviceName": "sensor",
          "propertyName": "temp"
        }
      },
      "right": {
        "type": "constant",
        "name": "offset",
        "parameters": {
          "value": 273.15
        }
      }
    },
    "bindings": {}
  }
}
```

##### Parameters

* `expression` (`object`, required): Serialized root `ValueStateExpression` AST node.
* `bindings` (`object`, optional): Map of symbol names to nested `ValueStateConfiguration` objects resolved by `"symbol"` nodes.

##### Expression Node Types and Operations

An expression tree consists of the following node types:

###### 1. Unary Operations (`"type": "unary"`)

Applies a unary transform to a child expression node:

```json
{
  "type": "unary",
  "operation": "sqrt",
  "argument": {
    "type": "symbol",
    "symbol": "x"
  }
}
```

* `operation` (`string`, required):
  * Arithmetic negation: `"-"`, `"negate"`, `"negative"`
  * Trigonometric: `"sin"`, `"cos"`
  * Common math: `"abs"`, `"sqrt"`, `"exp"`, `"ln"`
  * Time derivative: `"diff"`, `"differentiate"` (rate of change in units/second)
  * Time integration: `"integrateMinute"` (1 min), `"integrateHour"` (1 hr), `"integrateDay"` (1 day)
* `argument` (`object`, required): Child `ValueStateExpression` node.
* `parameters` (`object`, optional): Optional node parameters.

###### 2. Binary Operations (`"type": "binary"`)

Combines two child expression nodes with a binary operator:

```json
{
  "type": "binary",
  "operation": "*",
  "left": {
    "type": "symbol",
    "symbol": "flow"
  },
  "right": {
    "type": "constant",
    "name": "factor",
    "parameters": {
      "value": 1.2
    }
  }
}
```

* `operation` (`string`, required):
  * Addition: `"+"`, `"plus"`
  * Subtraction: `"-"`, `"minus"`
  * Multiplication: `"*"`, `"times"`, `"multiply"`
  * Division: `"/"`, `"div"`, `"divide"`
* `left` (`object`, required): Left child `ValueStateExpression` node.
* `right` (`object`, required): Right child `ValueStateExpression` node.
* `parameters` (`object`, optional): Optional node parameters.

###### 3. N-ary Functions (`"type": "function"`)

Evaluates a multi-argument function over named argument expressions:

```json
{
  "type": "function",
  "operation": "mean",
  "arguments": {
    "t1": { "type": "symbol", "symbol": "temp1" },
    "t2": { "type": "symbol", "symbol": "temp2" },
    "t3": { "type": "symbol", "symbol": "temp3" }
  }
}
```

* `operation` (`string`, required):
  * `"sum"`: Computes the sum of all non-null argument values.
  * `"mean"`, `"average"`: Computes the arithmetic mean of non-null argument values.
  * `"integrate"`: Time integral with arguments `"arg"` (expression node) and `"windows"` (constant duration in seconds).
* `arguments` (`object`, required): Map of argument names to child `ValueStateExpression` nodes.
* `parameters` (`object`, optional): Optional node parameters.

###### 4. Constants (`"type": "constant"`)

Supplies a static numeric value or standard mathematical constant:

```json
{
  "type": "constant",
  "name": "pi"
}
```

* `name` (`string`, required):
  * Built-in mathematical constants: `"pi"`, `"Pi"`, `"PI"` ($\pi \approx 3.1415926535$), `"e"` ($e \approx 2.7182818284$).
  * Named literal: Any custom identifier; the numeric value is supplied via `parameters.value`.
* `parameters` (`object`, optional): Object containing `"value"` (`number`) when specifying literal constants (e.g. `{"value": 42.0}`).

###### 5. Symbols (`"type": "symbol"`)

Resolves a variable bound in `bindings` or ambient evaluation context:

```json
{
  "type": "symbol",
  "symbol": "temperature"
}
```

* `symbol` (`string`, required): The identifier key mapped in `bindings`.

###### 6. State Adapters (`"type": "state"`)

Directly embeds and instantiates a `ValueState` using any registered factory (e.g., `deviceProperty`, `tagTable`, `math`):

```json
{
  "type": "state",
  "valueStateType": "deviceProperty",
  "parameters": {
    "deviceName": "pump1",
    "propertyName": "speed"
  },
  "valuePath": "",
  "defaultValue": 0.0
}
```

* `valueStateType` (`string`, required): Registered value state factory name.
* `parameters` (`object`, required): Parameters required by the target state factory.
* `valuePath` (`string`, optional, default: `""`): Property path extracted from `Meta`.
* `defaultValue` (`number | null`, optional): Fallback value if the underlying state is null.

#### 4. Tag Table (`tagTable` / `controls.tags`)

Connects the property to an external physical tag source (OPC-UA, Modbus, PLC4X) managed by `TagTablePlugin`.

```json
{
  "type": "tagTable",
  "parameters": {
    "tag": "opc.boiler.pressure",
    "table": "@table"
  }
}
```

* `tag` (`string`, required): Tag identifier in the tag table.
* `table` (`string`, optional, default: `"@table"`): Tag table name.

---

## Components (`TemplateDeviceConfiguration`)

The `components` object defines template/virtual devices instantiated from registered `DeviceTreeFactory` or
`DeviceFactory` plugins.

```json
"components": {
  "alarm1": {
    "type": "controls.utilities.alarm",
    "parameters": {
      "settings": [
        {
          "lowerThreshold": -5.0,
          "upperThreshold": 5.0,
          "status": "OUT5"
        },
        {
          "lowerThreshold": -10.0,
          "upperThreshold": 10.0,
          "status": "OUT10"
        }
      ]
    },
    "metadata": {}
  }
}
```

### Fields

| Field        | Type     | Default      | Description                                                                                                                         |
|--------------|----------|--------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `type`       | `string` | **required** | Registered factory name of the component (e.g., `"controls.utilities.alarm"`, `"controls.utilities.accumulator"`, `"computation"`). |
| `parameters` | `object` | `{}`         | Initialization parameters passed to the factory.                                                                                    |
| `metadata`   | `object` | `{}`         | Optional component metadata.                                                                                                        |

### Common Template Device Types

* **Alarm (`controls.utilities.alarm` / `alarm`)**: Evaluates incoming numeric samples against ordered threshold rules
  and exposes a `"state"` property with the alarm status.
    * `settings` (`array`): List of rule objects:
        * `lowerThreshold` (`number | null`): Lower bound (matches when input is strictly below).
        * `upperThreshold` (`number | null`): Upper bound (matches when input is strictly above).
        * `status` (`string`): Status code reported when rule matches.

* **Accumulator (`controls.utilities.accumulator` / `accumulator`)**: Computes a rolling window sum of incoming numeric
  samples and exposes a `"state"` property.
    * `window` (`string | number`): Window duration string (e.g., `"5s"`, `"1m"`) or seconds.

* **Computation Device (`computation` / `controls-expression.computation`)**: Evaluates dynamic mathematical formulas
  over bound inputs and publishes the computed value as `"result"`.
    * `argNames` (`array` of `string`): Input port names.
    * `formula` (`string`): Mathematical formula using input port names.

---

## Bindings (`ConstructorBinding`)

The `bindings` array specifies reactive connections routing data from device properties to component inputs
(`BoundStateHolder`).

```json
"bindings": [
  {
    "sourceDevice": "",
    "sourceProperty": "temperature",
    "targetDevice": "alarm1",
    "targetInput": "default",
    "defaultValue": 0.0,
    "metadata": {}
  }
]
```

### Fields

| Field            | Type     | Default      | Description                                                                                                            |
|------------------|----------|--------------|------------------------------------------------------------------------------------------------------------------------|
| `sourceDevice`   | `string` | `""`         | Source device name/path relative to the current container. An empty string `""` refers to the container device itself. |
| `sourceProperty` | `string` | **required** | Property name on the source device to read from.                                                                       |
| `targetDevice`   | `string` | **required** | Target component name (must implement `BoundStateHolder`, such as an alarm or accumulator).                            |
| `targetInput`    | `string` | `"default"`  | Target input port name on the component (`"default"` or custom named port).                                            |
| `defaultValue`   | `any`    | `{}`         | Fallback initial value before the source property emits.                                                               |
| `metadata`       | `object` | `{}`         | Optional binding metadata.                                                                                             |

---

## Sub-Devices (`devices`)

The `devices` map allows arbitrary hierarchical nesting of device configurations:

```json
"devices": {
  "subSystemA": {
    "properties": {
      "flow": {
        "type": "tagTable",
        "parameters": {
          "tag": "plc.flow1"
        }
      }
    },
    "devices": {},
    "components": {},
    "bindings": []
  }
}
```

Nested devices can be referenced across the hierarchy using dot-separated paths (e.g., `"subSystemA"` or
`"parent.child"`).

---

## Complete Example

Below is a complete example of a device configuration with tag properties, computed math properties, nested sub-devices,
virtual alarm components, and bindings:

```json
{
  "properties": {
    "temperature": {
      "type": "tagTable",
      "parameters": {
        "tag": "opc.sensor.temp"
      }
    },
    "pressure": {
      "type": "tagTable",
      "parameters": {
        "tag": "opc.sensor.pressure"
      }
    },
    "compensatedTemperature": {
      "type": "math",
      "parameters": {
        "expression": "temperature * 1.05 + 0.2",
        "arguments": {
          "temperature": {
            "type": "tagTable",
            "parameters": {
              "tag": "opc.sensor.temp"
            }
          }
        }
      }
    }
  },
  "components": {
    "tempAlarm": {
      "type": "controls.utilities.alarm",
      "parameters": {
        "settings": [
          {
            "lowerThreshold": 10.0,
            "upperThreshold": 80.0,
            "status": "WARN"
          },
          {
            "lowerThreshold": 0.0,
            "upperThreshold": 100.0,
            "status": "CRITICAL"
          }
        ]
      }
    }
  },
  "bindings": [
    {
      "sourceDevice": "",
      "sourceProperty": "compensatedTemperature",
      "targetDevice": "tempAlarm",
      "targetInput": "default"
    }
  ],
  "devices": {
    "subPump": {
      "properties": {
        "speed": {
          "type": "tagTable",
          "parameters": {
            "tag": "modbus.pump.speed"
          }
        }
      }
    }
  }
}
```

---

## Kotlin Usage

### Deserializing and Constructing Devices

```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import space.kscience.controls.constructor.ConstructorDeviceConfiguration
import space.kscience.controls.constructor.ConstructorPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import java.io.InputStream

fun loadAndConstruct(context: Context, inputStream: InputStream) {
    val configuration = Json.decodeFromStream<ConstructorDeviceConfiguration>(inputStream)
    val constructorPlugin = context.request(ConstructorPlugin)
    val deviceTree = constructorPlugin.construct(configuration)
}
```

### Serializing Configuration to JSON

```kotlin
import kotlinx.serialization.json.Json
import space.kscience.controls.constructor.ConstructorDeviceConfiguration

val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

val jsonString = json.encodeToString(ConstructorDeviceConfiguration.serializer(), configuration)
```

<!-- LLM generated code: Documentation for ConstructorDeviceConfiguration JSON structure -->
