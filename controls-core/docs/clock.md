# Clock Management

The `clock` feature in `Controls-kt` provides a flexible way to manage time within the device ecosystem. It allows for platform-independent time access, time compression for speeding up simulations, and virtual time for deterministic testing.

## ClockManager

`ClockManager` is a DataForge `Plugin` that manages the time source for a `Context`. It provides access to a `Clock` and a `CoroutineDispatcher` that are synchronized with the chosen time mode.

### Time Modes

The `ClockManager` supports several operational modes:

1.  **System**: Uses the real system time (`Clock.System`). This is the default mode.
2.  **Compressed**: Scales the passage of time by a compression factor. For example, a compression of 10.0 makes time pass 10 times faster than real time. This is useful for long-running simulations.
3.  **Virtual**: Uses a manual time scheduler. Time only advances when tasks are waiting or when explicitly moved. This is ideal for unit tests to ensure deterministic behavior.
4.  **Custom**: Allows providing a custom implementation of `kotlin.time.Clock`.

## Working with ClockManager

You can configure the clock mode when creating a `Context`:

```kotlin
val context = Context {
    // Enable virtual time
    withVirtualTime()
    
    // OR enable time compression
    // withTimeCompression(10.0)
}

// Access the managed clock
val clock = context.clock

// Access the simulation dispatcher
launch(context.simulationDispatcher) {
    delay(1.seconds) // This will use virtual/compressed delay
}
```

## Time Utilities

### ValueWithTime

A simple wrapper that associates a value with a timestamp:

```kotlin
val timedValue = ValueWithTime(value, clock.now())
```

### PropertyHistory

`Controls-kt` provides utilities to keep track of property changes over time. You can use `PropertyHistory` to store and query historical data of a device property.

## Demos and Tests

-   **Tests**:
    -   `../src/commonTest/kotlin/space/kscience/controls/time/VirtualTimeTest.kt`: Tests for virtual time advancement and scheduling.
-   **Demos**:
    -   `../../demo/constructor`: Extensive use of virtual time and `ClockManager` for multi-device simulations.
    -   `../../demo/thermo`: Uses `ClockManager` to synchronize the simulator and the control logic.

<!-- LLM generated code: Documentation for Clock Management feature -->
