# Controls-kt-core

Core interfaces and building blocks for Controls.kt device servers. This module defines the device API, typed specifications, message model, simple device composition primitives, and utilities commonly used across the ecosystem.

The code is Kotlin Multiplatform (JVM, JS, Native, Wasm JS) and depends on kotlinx.coroutines and DataForge core types (Meta, IO).

## What’s inside

- API
  - Device: the main lifecycle-aware, coroutine-scope interface to read/write properties and execute actions.
  - DeviceMessage: a sealed message model to describe property changes, gets/sets, action execution/results, logging, lifecycle, and binary notifications.
  - DeviceHub: a tree-like composition of devices that routes messages and aggregates descriptions.
  - WithLifeCycle: common lifecycle utilities for devices.
- Spec (Type-safe DSL)
  - DeviceSpec, DeviceBase, DeviceBySpec: split of device description (specification) from its runtime state.
  - Property and Action descriptors, delegates for defining meta-backed, typed properties.
  - Helpers to create device instances from specs on each target.
- Manager
  - DeviceManager: DataForge DI integration and registry to build/find devices by names.
  - respondMessage: helper to respond to incoming DeviceMessage queries.
- Ports
  - Asynchronous/Synchronous port abstractions to send and receive raw byte arrays.
  - Common helpers to build framed protocols and I/O extensions.
  - JVM implementations: ChannelPort, UdpSocketPort; Ktor and Serial implementations live in separate modules.
- Time utilities (see [detailed documentation](time-management.md))
  - ClockManager and resolveClock for platform clocks.
  - ValueWithTime and PropertyHistory to provide local device state history data.
  - VirtualTimeDispatcher: a virtual-time coroutine dispatcher useful for simulation and deterministic tests.
- Binary frames (see [detailed documentation](binary-frames.md))
  - PeerConnection: a thin abstraction for point-to-point message exchange between devices/hubs.
  - FrameProcessor: middleware for reactive binary data processing with telemetry and monitoring.
  - FrameProcessingGraph: DSL for building and monitoring complex processing pipelines.
- Converters
  - Meta converters and IO formats for common types (Duration, Instant, Double ranges), plus convenience extensions.

## Features:

${features}

${artifact}

## Notes

- Maturity: EXPERIMENTAL (APIs may change).
- This module uses DataForge Meta to represent values; see [dataforge-core](https://git.sciprog.center/kscience/dataforge-core) for details.
- For examples and demos, check the [demo](../demo) modules in the root project and controls-constructor for high-level composition and simulation.