# Architecture of controls-data-platform module

## Purpose and Goals

The `controls-data-platform` module is designed to create a unified platform for collecting and managing time-series data from various industrial devices and protocols. The main task of the module is to abstract low-level details of interaction with equipment and provide a unified data access interface within the `controls-kt` ecosystem.

Key features:
* Support for OPC UA, Modbus (RTU and TCP) protocols, and various interfaces via Apache PLC4X.
* Time series collection with data averaging and aggregation capabilities.
* Integration with the `controls-kt` object model via `DataPlatformDevice`.
* Use of `kmath` for mathematical processing of data streams.

## Design and Core Components

The module's architecture is built around several key entities:

### 1. DataPlatform
The central class that manages the lifecycle of connections to external sources. It contains the configuration (`DataPlatformConfiguration`) and caches clients for various protocols (OPC UA, PLC, Modbus), ensuring their reuse and correct closure.

### 2. DataPlatformDevice
An implementation of the `Device` interface from `controls-core` that wraps `DataPlatform`. This allows representing a collection of data from various sources as a single virtual device. The device periodically polls sources according to configured timers and publishes property changes via `messageFlow`.

### 3. Time Series Collection Mechanisms
The module provides tools for working with streaming data:
* **TimeSeriesSource**: an interface for a time series data source.
* **TimeSeriesCollector**: a component that collects data from sources, aligns them on a time grid, and performs basic aggregation (e.g., averaging).
* **RollingSeries**: a data structure for storing a fixed window of recent values (rolling window) using `kmath` buffers.

## Architectural Diagram

A visual representation of the architecture is available in draw.io format:
[Architecture diagram (architecture.drawio)](architecture.drawio)

## Libraries and Tools Used

The module relies on the following technological solutions:

*   **[Eclipse Milo](https://github.com/eclipse/milo)**: a library for working with the OPC UA protocol.
*   **[Apache PLC4X](https://plc4x.apache.org/)**: a universal driver for interacting with industrial PLCs (Programmable Logic Controllers).
*   **[j2mod](https://github.com/steveohara/j2mod)**: a library for implementing the Modbus protocol.
*   **[KMath (space.kscience.kmath)](https://github.com/SciProgCentre/kmath)**: a library for mathematical calculations, used for managing data buffers and performing operations on time series.
*   **[Kotlin Coroutines & Flow](https://github.com/Kotlin/kotlinx.coroutines)**: the foundation for asynchronous interaction and processing data streams from devices.
*   **[DataForge](https://github.com/SciProgCentre/dataforge-core)**: the infrastructural basis for configuration (Meta) and context management (Context).

## Interaction with Other Modules

*   **controls-core**: base device and message APIs.
*   **controls-opcua**, **controls-plc4x**, **controls-modbus**: specialized driver modules.
*   **controls-constructor**: used for declarative description of platform configurations.

## Features:



## Artifact:

The Maven coordinates of this project are `space.kscience:controls-data-platform:0.4.0-dev-9`.

**Gradle Kotlin DSL:**
```kotlin
repositories {
    maven("https://repo.kotlin.link")
    mavenCentral()
}

dependencies {
    implementation("space.kscience:controls-data-platform:0.4.0-dev-9")
}
```

## Notes

- Maturity: EXPERIMENTAL (APIs may change).
- This module uses DataForge Meta to represent values; see [dataforge-core](https://git.sciprog.center/kscience/dataforge-core) for details.
- For examples and demos, check the [demo](../demo) modules in the root project and controls-constructor for high-level composition and simulation.