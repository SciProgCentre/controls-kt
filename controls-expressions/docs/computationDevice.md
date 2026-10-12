# ComputationDevice

`ComputationDevice` is a virtual device (`DeviceConstructor` and `BoundStateHolder`) that performs mathematical calculations on dynamic input streams and publishes the calculated value as its `result` property.

## Overview

- **Input Ports**: Declared via `argNames`. Inputs can be bound at runtime via `bind(state, inputName)` or declaratively via `ConstructorBinding`.
- **Result Property**: Automatically registers a read-only property `"result"` (`ValueState<Double?>`) that updates reactively as inputs change.
- **Dual Computation Engine**: Supports both KMath-AST formula expressions and structured `ValueStateExpression` trees.

## Programmatic Usage

### Formula-Based Computation (`ofMath`)

Construct a computation device using a mathematical formula string parsed via KMath-AST:

```kotlin
val device = ComputationDevice.ofMath(
    context = context,
    mst = "sqrt(a * a + b * b)".parseMath(),
    argNames = listOf("a", "b")
)

// Bind inputs dynamically
device.bind(sensorAState, "a")
device.bind(sensorBState, "b")

println(device.result.value) // Evaluated hypotenuse
```

### Expression-Based Computation (`ofExpression`)

Construct a computation device using a structured `ValueStateExpression`:

```kotlin
val device = ComputationDevice.ofExpression(
    context = context,
    expression = ValueStateExpression.Binary(
        operation = "+",
        left = ValueStateExpression.Symbol("x"),
        right = ValueStateExpression.Symbol("y")
    ),
    argNames = listOf("x", "y")
)

device.bind(stateX, "x")
device.bind(stateY, "y")
```

## Declarative Composition in ConstructorPlugin

`ComputationDevice` can be declared in `ConstructorDeviceConfiguration` using the factory type `"computation"` (or `"controls-expression.computation"`):

```kotlin
val configuration = ConstructorDeviceConfiguration(
    properties = mapOf(
        "voltage" to ValueStateConfiguration.math("220.0"),
        "current" to ValueStateConfiguration.math("5.0")
    ),
    components = mapOf(
        "powerCalculator" to TemplateDeviceConfiguration(
            type = ComputationDevice.TYPE,
            parameters = Meta {
                set(ComputationDevice.argNames, listOf("u", "i"))
                set(ComputationDevice.formula, "u * i")
            }
        )
    ),
    bindings = setOf(
        ConstructorBinding(
            sourceDevice = Name.EMPTY,
            sourceProperty = "voltage",
            targetDevice = Name.of("powerCalculator"),
            targetInput = "u"
        ),
        ConstructorBinding(
            sourceDevice = Name.EMPTY,
            sourceProperty = "current",
            targetDevice = Name.of("powerCalculator"),
            targetInput = "i"
        )
    )
)

val tree = context.request(ConstructorPlugin).construct(configuration)
val power = tree.resolvePropertyState(Name.of("powerCalculator"), "result").value.double
// power == 1100.0
```

<!-- LLM generated code: Documentation for ComputationDevice -->
