# Controls-kt-core

Core interfaces and building blocks for Controls.kt device servers. This module defines the device API, typed specifications, message model, simple device composition primitives, and utilities commonly used across the ecosystem.

The code is Kotlin Multiplatform (JVM, JS, Native, Wasm JS) and depends on kotlinx.coroutines and DataForge core types (Meta, IO).

## What’s inside

- API
  - [Device](./device.md): the main lifecycle-aware, coroutine-scope interface to read/write properties and execute actions.
  - [DeviceMessage](./deviceMessage.md): a sealed message model to describe property changes, gets/sets, action execution/results, logging, lifecycle, and binary notifications.
  - [DeviceHub](./deviceHub.md): a tree-like composition of devices that routes messages and aggregates descriptions.
  - WithLifeCycle: common lifecycle utilities for devices.
- [Spec](./deviceSpec.md) (Type-safe DSL)
  - DeviceSpec, DeviceBase, DeviceBySpec: split of device description (specification) from its runtime state.
  - Property and Action descriptors, delegates for defining meta-backed, typed properties.
  - Helpers to create device instances from specs on each target.
- [Manager](./deviceManager.md)
  - DeviceManager: DataForge DI integration and registry to build/find devices by names.
  - respondMessage: helper to respond to incoming DeviceMessage queries.
- [Ports](./ports.md)
  - Asynchronous/Synchronous port abstractions to send and receive raw byte arrays.
  - Common helpers to build framed protocols and I/O extensions.
  - JVM implementations: ChannelPort, UdpSocketPort; Ktor and Serial implementations live in separate modules.
- Time utilities
  - ClockManager and resolveClock for platform clocks.
  - ValueWithTime and PropertyHistory to provide local device state history data.
  - VirtualTimeDispatcher: a virtual-time coroutine dispatcher useful for simulation and deterministic tests.
- Peer
  - PeerConnection: a thin abstraction for point-to-point message exchange between devices/hubs.
- Converters
  - Meta converters and IO formats for common types (Duration, Instant, Double ranges), plus convenience extensions.

## Features:

${features}

${artifact}

## Notes

- Maturity: EXPERIMENTAL (APIs may change).
- This module uses DataForge Meta to represent values; see [DataForge concepts](../../docs/DataForge.md) and [dataforge-core](https://git.sciprog.center/kscience/dataforge-core) for details.
- For examples and demos, check the [demo](../demo) modules in the root project and controls-constructor for high-level composition and simulation.