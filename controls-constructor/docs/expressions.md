# Property Expressions

Property expressions in `controls-constructor` provide a way to define computed device properties using an abstract-tree-based expression system. These expressions are reactive, meaning they automatically update when their dependencies (such as other device properties) change.

## StateExpression

The core of the system is the `StateExpression` interface, which represents a node in the expression tree. It can be one of the following:

### Constants
Represent fixed values.
- `Constant("pi")`: The value of $\pi$ (approx. 3.14159).
- `Constant("e")`: The base of natural logarithms $e$ (approx. 2.71828).

### Device Properties
Link to an existing property of a device in the `DeviceHub`.
- `Property(deviceName, propertyName, path, parameters)`: 
  - `deviceName`: The name of the device (as `Name`).
  - `propertyName`: The name of the property on that device.
  - `path`: (Optional) If the property is a `Meta` object, this specifies the path to the numeric value within it.

### Unary Operations
Apply a function to a single argument.
- `-`, `negate`, `negative`: Negation.
- `sin`, `cos`: Trigonometric functions.
- `abs`: Absolute value.
- `sqrt`: Square root.
- `exp`: Exponential function ($e^x$).
- `ln`: Natural logarithm.
- `diff`, `differentiate`: Time derivative of the state.

### Binary Operations
Apply a function to two arguments.
- `+`, `plus`: Addition.
- `-`, `minus`: Subtraction.
- `*`, `times`, `multiply`: Multiplication.

### Nary Operations
Apply a function to multiple arguments.
- `sum`: Sum of all provided arguments.

## Using Expressions in DeviceConstructor

To use a `StateExpression` in a `DeviceConstructor`, you can use the `expression` delegate. This registers a new property that is automatically computed based on the expression.

```kotlin
class MyDevice(context: Context) : DeviceConstructor(context) {
    val x by virtualProperty(MetaConverter.double, 1.0)
    val y by virtualProperty(MetaConverter.double, 2.0)

    // A computed property z = x + y
    val z by expression(
        StateExpression.Binary(
            operation = "+",
            left = StateExpression.Property(deviceName = "test".asName(), propertyName = "x"),
            right = StateExpression.Property(deviceName = "test".asName(), propertyName = "y")
        )
    )
}
```
A property initialized this way is accessible only after device is started (`start` method is called and lifecycle is `STARTING`). It will throw an exception in other cases. It is required so the device itself is properly registered in the `DeviceManager` before calling resolution. 

## Advanced Features

### Time Differentiation
The `diff` or `differentiate` unary operation computes the rate of change of a value with respect to time. This is useful for calculating speed from position, or power from energy.

```kotlin
val velocity by expression(
    StateExpression.Unary("diff", StateExpression.Property("device".asName(), "position"))
)
```

### Path Resolution
When a device property returns a complex `Meta` object, you can use the `path` parameter in `StateExpression.Property` to extract a specific numeric value from it.

## Demos and Tests

- **Tests**: `../src/commonTest/kotlin/space/kscience/controls/constructor/expressions/StateExpressionTest.kt`
- **Demos**: `../../demo/constructor`

<!-- LLM generated code: Documentation for Property Expressions feature -->

