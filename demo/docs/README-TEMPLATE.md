# Controls-kt demo

A collection of runnable examples that showcase how to build devices, simulations, servers, and user interfaces with
Controls.kt. The demos cover core device APIs, high-level composition with the constructor DSL, messaging with Magix,
OPC UA/Modbus integration, and visualization.

These projects are intended for learning, quick prototyping, and smoke-testing features across the stack. Most demos run
on the JVM; some include JS/Desktop frontends.

## What’s inside

Notable demo submodules in this directory:

- [thermo](thermo): A temperature monitoring demo with simulated Modbus devices, an OPC UA server, and a Compose-based
  dashboard. Shows hub-of-sensors pattern, trending graphs, and alarm states.
- [device-collective](device-collective): A demonstrator for multi-channel communication for autonomous device
  collective. Devices could be controlled directly by a central hub, or they could be controlled transitively via single
  master device. The demo also features map integration via [maps-kt](https://git.sciprog.center/kscience/maps-kt).
- [constructor](constructor): Minimal examples of high-level modeling
  with [controls-constructor](../controls-constructor) and its state DSL.
- [magix-demo](magix-demo): Messaging over Magix; demonstrates publishing/subscribing device messages between processes.
- [many-devices](many-devices): A stress-test for the Magix bus and the device API on tens of simultaneous massively
  messaging devices.
- [motors](motors): Simple motion control examples (mock motors) and property/action flows. Mirrors a solution
  for https://onlinelibrary.wiley.com/iucr/doi/10.1107/S1600577522002685.
- car: Toy device composition example demonstrating nested devices and property wiring.
- [all-things](all-things): Aggregates multiple demo pieces for local experiments. Including Magix event-bus with
  different flavors, opc-ua and different visualizers.
- [mks-pdr900](mks-pdr900): Example integration for an MKS PDR900 (for demonstration/testing when hardware is available)
  done for Troitsk nu-mass experiment.

## How things are built

- controls-core: Device API, typed specs, message model, and basic utilities used in every demo.
- controls-constructor: State-centric DSL and simulation helpers used in modeling-oriented demos.
- controls-ports-ktor, controls-serial, controls-opcua, controls-modbus, controls-pi, etc.: Optional modules that the
  specific demo may depend on.
- magix: Message bus to interconnect processes and devices.
- visualization: Some demos use Compose Multiplatform; others may use charts/plots where applicable.
