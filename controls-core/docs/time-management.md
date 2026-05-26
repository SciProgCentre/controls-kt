# Time Management in controls-core

The `space.kscience.controls.time` package provides tools for managing time in `controls-kt`. This is essential for both real-time device control and deterministic simulations.

## ClockManager

`ClockManager` is a DataForge `Plugin` that manages the `Clock` and `CoroutineDispatcher` used within a `Context`. It allows switching between different time modes without changing the application logic.

### Clock Modes

There are several supported `ClockMode`s:
- **System**: Uses the standard system clock (`Clock.System`).
- **Custom**: Allows providing a custom `Clock` implementation.
- **Compressed**: Speeds up or slows down time by a given factor. This is useful for running simulations faster than real-time.
- **Virtual**: Uses a `VirtualTimeDispatcher` to control time manually. This is ideal for deterministic tests and simulations where time should only advance when all scheduled tasks are completed.

### DSL for Context Configuration

You can configure the time mode when building a `Context` using the provided DSL:

```kotlin
// LLM generated code: Example of configuring Context with virtual time
val context = Context {
    withVirtualTime()
}
```

Or with time compression:

```kotlin
// LLM generated code: Example of configuring Context with time compression
val context = Context {
    withTimeCompression(2.0) // Runs 2 times faster
}
```

Extensions like `Context.clock` and `Context.simulationDispatcher` allow easy access to the configured time tools.

## VirtualTimeDispatcher

`VirtualTimeDispatcher` is a specialized `CoroutineDispatcher` that implements `Delay`. It allows for "skipping" delays in tests and simulations. Time in a `VirtualTimeDispatcher` only advances when:
- `advanceTimeBy(duration)` is called.
- `advanceUntilIdle()` is called.
- Tasks are scheduled for the future and the dispatcher is advanced.

It can be used to create a `Clock` that is synchronized with the coroutine execution using `asClock()`.

## ValueWithTime

`ValueWithTime<T>` is a simple data class wrapper that couples a value with the `Instant` it was recorded. It is widely used across `controls-kt` for representing time-stamped property values and sensor readings.

The package provides:
- `IOFormat` for binary serialization of `ValueWithTime`.
- `MetaConverter` for converting `ValueWithTime` to and from DataForge `Meta`.
- Extension `MetaConverter<T>.withTime()` to easily wrap existing converters.
