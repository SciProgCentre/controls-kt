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

- Adds samples when their timestamp is newer than previous samples.
- Discards samples that fall outside the sliding time window.
- Out-of-order samples are ignored.

### 2. Time Window Accumulation (`accumulate`)

Computes the sum of observed numeric samples within a sliding `Duration` window:

```kotlin
val accumulatedFlow = eventCountState.accumulate(
    window = 1.minutes,
    scope = coroutineScope
)
```

If `startingValue` is omitted or null, use the current source sample as the initial value.
A matching first subscription sample is not added again. A non-null `startingValue`
is a separate initial sample, even if it equals the first source sample.

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
