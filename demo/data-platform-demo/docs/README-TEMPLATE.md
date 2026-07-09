# Data Platform Demo

This demo showcases a data platform that aggregates data from multiple sources (OPC UA and Modbus) and provides a unified view and storage for the data.

## Features

- **Multi-source Data Collection**: Aggregates data from simulated OPC UA and Modbus devices.
- **Unified Property View**: All properties from various sources are accessible through a single `DataPlatformDevice`.
- **Data Storage**: Periodically stores collected data into the local filesystem.
- **Dynamic Configuration**: Loads platform configuration from a JSON file.

## Documentation

- [UI Specification](device-visualisation.md): Specification for the Compose Multiplatform-based visualization UI.
- [Storage Specification](storage-specification.md): Specification for the controls tag table storage store and restore procedures using `TableStorageIndex`.

## How to run

1. Run the `main` function in `space.kscience.controls.demo.main.kt`.
2. The demo will start simulated devices, configure the platform, and start the storage process.
3. Type `exit` in the console to stop the demo.
