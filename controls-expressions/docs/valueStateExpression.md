# ValueStateExpression

`ValueStateExpression` is a tree-based, serializable expression model for declarative calculation of reactive `ValueState<Double?>` values. It enables storing, serializing, and dynamically evaluating structured expression trees without compiling code.

## Expression Tree Nodes

The `ValueStateExpression` sealed interface provides the following node types:

- **Constant**: Fixed values such as `"pi"`, `"e"`, or custom numbers via `parameters["value"]`:
  ```kotlin
  ValueStateExpression.Constant("pi", Meta.EMPTY)
  ValueStateExpression.Constant("gravity", Meta { "value" put 9.81 })
  ```
- **Symbol**: Refers to a named variable resolved from the evaluation context:
  ```kotlin
  ValueStateExpression.Symbol("x")
  ```
- **Unary**: Unary operations applied to a child expression:
  ```kotlin
  ValueStateExpression.Unary("sin", argument)
  ```
  Supported operations: `-`, `negate`, `negative`, `sin`, `cos`, `abs`, `sqrt`, `exp`, `ln`, `diff`, `differentiate`, plus plugin extensions.
- **Binary**: Binary operations combining two expressions:
  ```kotlin
  ValueStateExpression.Binary("+", left, right)
  ```
  Supported operations: `+`, `plus`, `-`, `minus`, `*`, `times`, `multiply`, `/`, `div`, `divide`, plus plugin extensions.
- **Function**: Multi-argument named functions:
  ```kotlin
  ValueStateExpression.Function(
      operation = "mean",
      arguments = mapOf("a" to exprA, "b" to exprB, "c" to exprC)
  )
  ```
  Supported operations: `sum`, `mean`, `average`, plus plugin extensions.
- **State**: Dynamically resolves a value state via `ConstructorPlugin` (e.g. `deviceProperty`):
  ```kotlin
  ValueStateExpression.deviceProperty("sourceDevice", "propertyName")
  ```

## Context Evaluation

`StateExpressionContext` resolves and evaluates a `ValueStateExpression` into a live `ValueState<Double?>`:

```kotlin
val context = StateExpressionContext(dataForgeContext) { symbolName ->
    // resolve contextual symbol bindings
    symbolBindings[symbolName] ?: error("Unknown symbol: $symbolName")
}

val state = context.computeState(expression)
```

## Declarative State Factory

`ExpressionValueStateFactory` (type `"expression"` or `"controls-expression.expression"`) integrates expressions with `ConstructorPlugin`:

```kotlin
val config = ValueStateConfiguration.expression(
    ValueStateExpression.Binary(
        operation = "*",
        left = ValueStateExpression.Symbol("raw"),
        right = ValueStateExpression.Constant("scale", Meta { "value" put 2.5 })
    )
)
```

## Device Property Delegate

In a `DeviceConstructor`, you can expose an expression as a reactive property using the `expression` delegate:

```kotlin
class DerivedDevice(context: Context) : DeviceConstructor(context) {
    val a by virtualProperty(MetaConverter.double, 10.0)
    val b by virtualProperty(MetaConverter.double, 2.0)

    val ratio by expression(
        ValueStateExpression.Binary(
            operation = "/",
            left = ValueStateExpression.deviceProperty("derived", "a"),
            right = ValueStateExpression.deviceProperty("derived", "b")
        )
    )
}
```

<!-- LLM generated code: Documentation for ValueStateExpression -->
