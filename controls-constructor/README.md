# Controls-kt-constructor

A simplified constructor for composing and simulating devices using a state-centric, type-safe DSL. This module lets you
build virtual devices and high-level models by wiring reactive states together, exposing some of them as device
properties, and running deterministic or realtime simulations.

The code is Kotlin Multiplatform (JVM, JS, Native, Wasm JS) and is built on Controls.kt core primitives,
kotlinx.coroutines, and DataForge (Context/Meta).

## What’s inside

- Core concepts
    - ConstructorElement: a sealed marker for elements used in a construction graph.
        - PropertyConstructorElement: links a device property to a DeviceState.
        - StateConstructorElement: registers an independent state (e.g., timer, virtual value).
        - ConnectionConstructorElement: documents data flow between states (reads/writes edges).
        - ModelConstructorElement: nests a ModelConstructor inside another container.
    - StateContainer: a Context-aware, CoroutineScope host that manages states and connections.
    - Model and ModelConstructor: build reusable simulation models with their own coroutine context and dependencies.
    - DeviceConstructor: a DeviceGroup that also acts as a StateContainer; exposes states as device properties.

- State DSL (reactive building blocks)
    - stateOf, registerState: create/register states; DeviceState and MutableDeviceState abstractions.
    - timer and onTimer: periodic timer states; helpers to trigger logic on ticks.
    - runSimulation: run models on the context simulation dispatcher.
    - mapState, flowState: transform a state via pure or flow-based transformations.
    - combineState: combine 2, 3, 4, or many states into a derived state; map and associative variants supported.
    - bindState, bindTransformedState, bindCombinedState: push-style propagation from one/many source states into a
      mutable target state, with automatic registration of ConnectionConstructorElement.

- Device integration
    - property/mutableProperty/virtualProperty: expose external or virtual states as device properties with Meta
      converters and descriptors.
    - registerAsProperty: register a typed DeviceSpec property backed by a state.
    - device(factory/device): install sub-devices in a constructor via delegates.

- Simulation time and utilities
    - DefaultTimer presets (VERY_FAST..VERY_SLOW) and arbitrary Duration-based timers.
    - clock extension for StateContainer; deterministic virtual-time via Controls.kt time plugin.

- Continuous models
    - Delays: DeviceState.delayedBy; delayed producer/consumer wrappers to model transport/latency.
    - Limits: limited producer/consumer wrappers cap requests or supplies by AmountPerSecond.
    - Integration: collectAmountAsync integrates flow-like PerSecond values over duration.
    - Units support lives in the constructor.units package and related types (Amount, PerSecond, UnitsOfMatter, etc.).

## Features:



## Artifact:

The Maven coordinates of this project are `space.kscience:controls-constructor:0.4.0-dev-8`.

**Gradle Kotlin DSL:**
```kotlin
repositories {
    maven("https://repo.kotlin.link")
    mavenCentral()
}

dependencies {
    implementation("space.kscience:controls-constructor:0.4.0-dev-8")
}
```

## Notes

- Maturity: EXPERIMENTAL (APIs may evolve).
- Depends on controls-core and DataForge Context/Meta; see the root README for architecture overview.
- For examples and demos, check the demo modules in the root project; this module is ideal for high-level composition
  and simulation on top of controls-core devices.
