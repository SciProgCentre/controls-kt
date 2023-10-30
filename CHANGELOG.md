# Changelog

## Unreleased

### Added
- Device lifecycle message
- Low-code constructor

### Changed
- Property caching moved from core `Device` to the `CachingDevice`

### Deprecated

### Removed

### Fixed
- Property writing does not trigger change if logical state already is the same as value to be set.
- Modbus-slave triggers only once for multi-register write.

### Security

## 0.2.2-dev-1 - 2023-09-24

### Changed
- updating logical state in `DeviceBase` is now protected and called `propertyChanged()`
- `DeviceBase` tries to read property after write if the writer does not set the value.

## 0.2.1 - 2023-09-24

### Added
- Core interfaces for building a device server
- Magix service for binding controls devices (both as RPC client and server)
- A plugin for Controls-kt device server on top of modbus-rtu/modbus-tcp protocols
- A client and server connectors for OPC-UA via Eclipse Milo
- Implementation of byte ports on top os ktor-io asynchronous API
- Implementation of direct serial port communication with JSerialComm
- A combined Magix event loop server with web server for visualization.
- An API for stand-alone Controls-kt device or a hub.
- An implementation of controls-storage on top of JetBrains Xodus.
- A kotlin API for magix standard and some zero-dependency magix services
- Java API to work with magix endpoints without Kotlin
- MQTT client magix endpoint
- RabbitMQ client magix endpoint
- Magix endpoint (client) based on RSocket
- A magix event loop implementation in Kotlin. Includes HTTP/SSE and RSocket routes.
- Magix history database API
- ZMQ client endpoint for Magix
