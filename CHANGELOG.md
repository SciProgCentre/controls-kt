# Changelog

## Unreleased

### Added
- Add DeviceTreeSpec
- `TimerState` implements `Clock`.
- `setCachedValue` API for `CachingDevice` to directly manipulate cached value and solve circular write problem with virtual properties.
- Flow control simulation
- Value averaging plot extension
- PLC4X bindings
- Shortcuts to access all Controls devices in a magix network.
- `DeviceClient` properly evaluates lifecycle and logs
- `PeerConnection` API for direct device-device binary sharing
- DeviceDrawable2D intermediate visualization implementation
- New interface `WithLifeCycle`. Change Port API to adhere to it.

### Changed
- **BREAKING** Removed Model and replaced it with Device.
- **BREAKING** DeviceHub is renamed to `DeviceTree`, has optional `rootDevice` and its children are `DeviceTree`s instead of `Device`s.
- **BREAKING** DeviceHub works with string device names instead of `Name`.
- ValueState now works both with and without time mark. Time is automatically provided and could be accessed with several new methods. 
- **BREAKING** Full refactor of device spec and device builders. Now device spec is a separate content. There is a `DeviceFactory` that could be used to create both specification and device factory at the same time. 
- Add optional names to model states and submodels
- `StateContainer` renamed to `Constructor`
- Separate StateContainer and MutableStateContainer
- `DeviceState` renamed to `ValueState`
- Update logic of `DeviceState` to properly address subscriptions.
- `getProperty` renamed to `getCachedProperty` for `CachingDevice`
- Milo migrated to stable version
- Constructor properties return `DeviceState` in order to be able to subscribe to them
- Refactored ports. Now we have `AsynchronousPort` as well as `SynchronousPort`
- `DeviceClient` now initializes property and action descriptors eagerly.
- `DeviceHub` now works with `Name` instead of `NameToken`. Tree-like structure is made using `Path`. Device messages no longer have access to sub-devices.
- Add some utility methods to ports. Synchronous port response could be now consumed as `Source`.
- `DeviceLifecycleState` is replaced by `LifecycleState`.
- Time is now the mandatory first field of all device messages


### Deprecated

### Removed

### Fixed
- Fix a problem with rsocket endpoint with no filter.

### Security

## 0.3.0 - 2024-03-04

### Added

- Device lifecycle message
- Low-code constructor
- Automatic description generation for spec properties (JVM only)

### Changed

- Property caching moved from core `Device` to the `CachingDevice`
- `DeviceSpec` properties no explicitly pass property name to getters and setters.
- `DeviceHub.respondHubMessage` now returns a list of messages to allow querying multiple devices. Device server also returns an array.
- DataForge 0.8.0

### Fixed

- Property writing does not trigger change if logical state already is the same as value to be set.
- Modbus-slave triggers only once for multi-register write.
- Removed unnecessary scope in hub messageFlow

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
