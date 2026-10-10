# Time-Window Numeric Operations

The `controls-expressions` module provides extension functions on `ValueState<Double?>` for performing rolling temporal calculus and statistics on streaming data.

## Streaming Operations

### 1. Trapezoid Integration (`integrate`)

Calculates a rolling trapezoid time integral over a sliding `Duration` window:

```kotlin
val integratedFlow = powerState.integrate(
    window = 1.hours,
    scope = coroutineScope
)
```

`startingValue` is the first signal sample, not an integral offset. The integral starts at zero.
The default uses the source value at construction, replacing null with zero.
`Instant.DISTANT_PAST` does not define an integration interval.

- Only newer timestamps advance the result; equal and older timestamps are ignored.
- Segments are clipped at the left window boundary by linear interpolation.
- Null samples advance the window without adding a point; interpolation connects non-null samples.
- No area is added before the first point or after the last one. There is no expiry timer.
- A zero window gives zero; an infinite window keeps all timed history. Negative windows are rejected.

### 2. Time Window Accumulation (`accumulate`)

Computes the sum of observed numeric samples within a sliding `Duration` window:

```kotlin
val accumulatedFlow = eventCountState.accumulate(
    window = 1.minutes,
    scope = coroutineScope
)
```

The default starting value is `0.0` at `Instant.DISTANT_PAST`.
The current source sample is added when the subscription starts.
An explicit `startingValue` is a separate sample in the window.

Samples older than the current result are ignored; equal timestamps are accepted.
Accepted null samples add nothing but advance the window and result time.
The lower window boundary is included; there is no expiry timer.
This operation does not integrate over time or compute changes in a cumulative counter.
A conflating source can skip intermediate samples.

### 3. Time Differentiation (`differentiate`)

Calculates the instantaneous rate of change ($\Delta v / \Delta t$) with respect to elapsed time in seconds:

```kotlin
val velocityState = positionState.differentiate(scope = coroutineScope)
```

- Evaluates derivative between consecutive timed samples.
- Ignores null samples and invalid timestamps.

## Plugin Operations

When `ControlsExpressionPlugin` is installed in the `Context`, these time-based operations are exposed to the expression engine:
- `diff`: Unary differentiation
- `integrateMinute`: 1-minute rolling integral
- `integrateHour`: 1-hour rolling integral
- `integrateDay`: 1-day rolling integral
- `integrate(arg, windows)`: Configurable duration integral function

<!-- LLM generated code: Documentation for Time-Window Numeric Operations -->
