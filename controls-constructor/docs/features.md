# Key Features of controls-constructor

The `controls-constructor` module provides a high-level, type-safe DSL for composing control systems and building simulations. It is built on top of the `controls-kt` core and leverages `kotlinx.coroutines` and `DataForge`.

## 1. Reactive State Abstraction

At the heart of the constructor is the `ValueState` interface, which represents a reactive value that can be observed over time. See [Value State](./valueState.md) for more details.

- **ValueState<T>**: A read-only observable state. It provides a `Flow` of values and their associated timestamps.
- **MutableValueState<T>**: A state that can be updated (emitted to).
- **Automatic Lifecycle**: States are managed within a `CoroutineScope`, ensuring proper cleanup and resource management.

## 2. State Composition and Transformation

States can be easily transformed or combined to create derived states:

- **map**: Transform a `ValueState<T>` into a `ValueState<R>` using a pure function.
- **transform**: Asynchronous transformation using coroutines.
- **combine**: Combine multiple states (2, 3, 4, or a collection/map) into a single derived state.
- **flowState**: Create a state from a `Flow` or using a flow-like builder.

## 3. Reactive Wiring (Binding)

The module simplifies the data flow between states using binding functions:

- **bindState**: Synchronize the value of a source state to a target mutable state.
- **bindTransformedState**: Bind a transformed value from a source to a target.
- **bindCombinedState**: Bind the result of combining multiple sources to a target.

These bindings are automatically registered as `ConnectionConstructorElement` in the construction graph, allowing for visualization and analysis of the system structure.

## 4. Device and Group Construction

The `DeviceConstructor` and `DeviceGroup` classes allow for hierarchical composition of devices. See [Device Construction](./constructor.md) for more details.

- **DeviceGroup**: A container for sub-devices and properties.
- **DeviceConstructor**: Extends `DeviceGroup` with `MutableConstructor` capabilities, allowing it to manage internal states and their connections.
- **Property Delegates**: Easily expose states as device properties using `property`, `mutableProperty`, or `virtualProperty`.
- **Sub-device Installation**: Install other devices using the `device` delegate.

## 5. Property Expressions

Define computed properties using a tree-based expression system. These expressions are reactive and automatically update when their dependencies change.

See [Property Expressions](expressions.md) for more details.

## 6. Simulation and Modeling

The module provides specialized tools for building simulation models. See [Simulation Models](./models.md) and [Flow Models](./flowModels.md) for more details.

- **Model and ModelConstructor**: Create reusable simulation components.
- **Continuous Models**: Pre-built components for modeling continuous flows:
    - `ContinuousProducer`, `ContinuousConsumer`, `ContinuousBuffer`.
    - `ContinuousMix`, `ContinuousSeparate`, `ContinuousReaction`.
- **Discrete Models**: Support for discrete-event modeling.
- **Time Management**: Integration with virtual time and simulation dispatchers for deterministic simulations.

## 7. Unit-Aware Modeling

Built-in support for typed measurements and rates. See [Unit-Aware Modeling](./units.md) for more details.

- **Amount<U>**: Represents a quantity with units.
- **PerSecond<U>**: Represents a rate of change.
- **Units support**: Located in `space.kscience.controls.constructor.units`, providing type safety for physical quantities in models.

<!-- LLM generated code: Key Features of controls-constructor -->
