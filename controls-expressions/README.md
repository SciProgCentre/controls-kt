# Module controls-expressions

Mathematical expressions and functions.

- [ValueStateAlgebra & KMath-AST Parsing](./docs/algebra.md): Mathematical algebra and KMath-AST formula parsing for reactive `ValueState` streams.
- [ValueStateExpression](./docs/valueStateExpression.md): Serializable expression trees, context evaluation, and device property delegates.
- [ComputationDevice](./docs/computationDevice.md): Virtual calculation device with dynamic input bindings and reactive result properties.
- [Time-Window Numeric Operations](./docs/numericState.md): Sliding-window integration, accumulation, and time differentiation.

## Quick Start

### Mathematical Formulas with ValueStateAlgebra

```kotlin
val a = MutableValueState(3.0)
val b = MutableValueState(4.0)

val result = ValueStateAlgebra.interpret(
    expression = "sqrt(a * a + b * b)",
    bindings = mapOf(Symbol("a") to a, Symbol("b") to b)
)
println(result.value) // 5.0
```

### Declarative Computation Devices

```kotlin
val calculator = ComputationDevice.ofMath(
    context = context,
    mst = "a * 2 + b".parseMath(),
    argNames = listOf("a", "b")
)

calculator.bind(stateA, "a")
calculator.bind(stateB, "b")
println(calculator.result.value)
```

## Usage

## Artifact:

The Maven coordinates of this project are `space.kscience:controls-expressions:0.5.0-dev-4`.

**Gradle Kotlin DSL:**
```kotlin
repositories {
    maven("https://repo.kotlin.link")
    mavenCentral()
}

dependencies {
    implementation("space.kscience:controls-expressions:0.5.0-dev-4")
}
```

<!-- LLM generated code: README-TEMPLATE for controls-expressions -->
