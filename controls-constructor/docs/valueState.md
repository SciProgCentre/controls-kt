# Reactive State Abstraction

The `valueState` feature provides the foundation for reactive data flow in `controls-constructor`. It revolves around the `ValueState` interface, which represents a value that can be observed over time.

## ValueState<T>

A `ValueState` is a read-only container for a value. It provides a `Flow` of values and their associated timestamps.

- **subscribe()**: Returns a `Flow<T>` of values.
- **subscribeWithTime()**: Returns a `Flow<ValueWithTime<T>>`, which includes timestamps.
- **value**: A property delegate to get the current value (if applicable, e.g., in a `CoroutineScope`).

## MutableValueState<T>

Extends `ValueState` and adds the ability to update the value.

- **emit(value)**: Updates the state with a new value.
- **setValue**: Property delegate to update the value.

## State Composition

`controls-constructor` provides many utility functions to transform and combine states:

### Mapping
Transform a state using a function.
```kotlin
val stringState: ValueState<String> = doubleState.map { it.toString() }
```

### Transformation
Asynchronous transformation using coroutines.
```kotlin
val transformedState = state.transform(scope, initialValue) { value ->
    delay(100)
    "Result: $value"
}
```

### Combination
Combine multiple states into one.
```kotlin
val combined = combine(scope, state1, state2) { s1, s2 -> s1 + s2 }
```
Support is provided for 2, 3, 4, or a collection/map of states.

## Demos and Tests

- **Tests**: `../src/commonTest/kotlin/space/kscience/controls/constructor/ValueStateTest.kt` (Currently missing, see [TODO.md](TODO.md))

<!-- LLM generated code: Documentation for ValueState feature -->
