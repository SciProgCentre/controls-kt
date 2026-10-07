# ValueStateAlgebra and KMath-AST Parsing

`ValueStateAlgebra` provides mathematical evaluation over reactive `ValueState<Double?>` streams within the `controls-kt` ecosystem. It implements KMath's `ExpressionAlgebra` and `ExtendedField`, enabling continuous symbolic and numeric transformations that preserve reactive data flow and timestamp tracking.

## Core Features

- **Reactive State Algebra**: Mathematical operations produce a dynamic `ValueState<Double?>` that recalculates when any input dependency emits.
- **KMath AST Integration**: Interprets expressions represented either as Mathematical Syntax Trees (`MST`) or as string formulas parsed via KMath-AST (`parseMath()`).
- **Timestamp Tracking**: Computes composite timestamps (`ValueWithTime`), automatically setting the output timestamp to the maximum timestamp among all dependent input states.
- **Extended Field Support**: Standard operations (`+`, `-`, `*`, `/`, unary minus, power) alongside elementary and trigonometric functions (`sin`, `cos`, `asin`, `acos`, `atan`, `exp`, `ln`, `scale`).

## Direct Evaluation

You can evaluate expressions directly in Kotlin code by providing variable bindings:

```kotlin
val a = MutableValueState(3.0)
val b = MutableValueState(4.0)

val bindings = mapOf(
    Symbol("a") to a,
    Symbol("b") to b
)

// Parse string formula with KMath-AST and interpret reactively
val hypotenuse = ValueStateAlgebra.interpret("sqrt(a * a + b * b)", bindings)

println(hypotenuse.value) // 5.0

// Reactive update
a.value = 6.0
b.value = 8.0
println(hypotenuse.value) // 10.0
```

## Declarative Math Factory

The `MathValueStateFactory` (type `"math"` or `"controls-expression.math"`) allows creating expression-backed states declaratively inside `ConstructorPlugin` configurations:

```kotlin
val propertyConfig = ValueStateConfiguration.math(
    expression = "sin(angle) * radius",
    arguments = mapOf(
        "angle" to ValueStateConfiguration.deviceProperty("sensor", "angle"),
        "radius" to ValueStateConfiguration.deviceProperty("sensor", "radius")
    )
)
```

## Extended Operations and Functions

`ValueStateAlgebra.interpret` accepts custom operation and function maps (such as those supplied by `ControlsExpressionPlugin`):
- Custom unary operations: e.g. `diff`, `integrateMinute`, `integrateHour`, `integrateDay`
- Custom multi-argument functions: e.g. `integrate(arg, windows)`

<!-- LLM generated code: Documentation for ValueStateAlgebra and KMath-AST parsing -->
