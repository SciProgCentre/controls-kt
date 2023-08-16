[![JetBrains Research](https://jb.gg/badges/research.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

# Controls.kt

Controls.kt (former DataForge-control) is a data acquisition framework (work in progress). It is based on DataForge, a software framework for automated data processing.
This repository contains a prototype of API and simple implementation
of a slow control system, including a demo.

Controls.kt uses some concepts and modules of DataForge,
such as `Meta` (tree-like value structure).

To learn more about DataForge, please consult the following URLs:
* [Kotlin multiplatform implementation of DataForge](https://github.com/mipt-npm/dataforge-core)
* [DataForge documentation](http://npm.mipt.ru/dataforge/)
* [Original implementation of DataForge](https://bitbucket.org/Altavir/dataforge/src/default/)

DataForge-control is a [Kotlin-multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html)
application. Asynchronous operations are implemented with
[kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) library.

## Materials and publications

* Video - [A general overview seminar](https://youtu.be/LO-qjWgXMWc)
* Video - [A seminar about the system mechanics](https://youtu.be/wES0RV5GpoQ)
* Article - [A Novel Solution for Controlling Hardware Components of Accelerators and Beamlines](https://www.preprints.org/manuscript/202108.0336/v1)

### Features
Among other things, you can:
- Describe devices and their properties.
- Collect data from devices and execute arbitrary actions supported by a device.
- Property values can be cached in the system and requested from devices as needed, asynchronously.
- Connect devices to event bus via bidirectional message flows.

Example view of a demo:

![](docs/pictures/demo-view.png)

## Documentation

* [Creating a device](docs/Device%20and%20DeviceSpec.md)

## Modules


### [controls-core](controls-core)
> 
>
> **Maturity**: EXPERIMENTAL
>
> **Features:**
> - [device](controls-core/src/commonMain/kotlin/space/kscience/controls/api/Device.kt) : Device API with subscription (asynchronous and pseudo-synchronous properties)
> - [deviceMessage](controls-core/src/commonMain/kotlin/space/kscience/controls/api/DeviceMessage.kt) : Specification for messages used to communicate between Controls-kt devices.
> - [deviceHub](controls-core/src/commonMain/kotlin/space/kscience/controls/api/DeviceHub.kt) : Grouping of devices into local tree-like hubs.


### [controls-magix-client](controls-magix-client)
> 
>
> **Maturity**: EXPERIMENTAL

### [controls-modbus](controls-modbus)
> 
>
> **Maturity**: EXPERIMENTAL

### [controls-opcua](controls-opcua)
> 
>
> **Maturity**: EXPERIMENTAL

### [controls-pi](controls-pi)
> 
>
> **Maturity**: EXPERIMENTAL

### [controls-ports-ktor](controls-ports-ktor)
> 
>
> **Maturity**: EXPERIMENTAL

### [controls-serial](controls-serial)
> 
>
> **Maturity**: EXPERIMENTAL

### [controls-server](controls-server)
> 
>
> **Maturity**: EXPERIMENTAL

### [controls-storage](controls-storage)
> 
>
> **Maturity**: PROTOTYPE

### [demo](demo)
> 
>
> **Maturity**: EXPERIMENTAL

### [magix](magix)
> 
>
> **Maturity**: EXPERIMENTAL

### [controls-storage/controls-xodus](controls-storage/controls-xodus)
> 
>
> **Maturity**: PROTOTYPE

### [demo/all-things](demo/all-things)
> 
>
> **Maturity**: EXPERIMENTAL

### [demo/car](demo/car)
> 
>
> **Maturity**: EXPERIMENTAL

### [demo/echo](demo/echo)
> 
>
> **Maturity**: EXPERIMENTAL

### [demo/magix-demo](demo/magix-demo)
> 
>
> **Maturity**: EXPERIMENTAL

### [demo/many-devices](demo/many-devices)
> 
>
> **Maturity**: EXPERIMENTAL

### [demo/mks-pdr900](demo/mks-pdr900)
> 
>
> **Maturity**: EXPERIMENTAL

### [demo/motors](demo/motors)
> 
>
> **Maturity**: EXPERIMENTAL

### [magix/magix-api](magix/magix-api)
> 
>
> **Maturity**: EXPERIMENTAL

### [magix/magix-java-client](magix/magix-java-client)
> 
>
> **Maturity**: EXPERIMENTAL

### [magix/magix-mqtt](magix/magix-mqtt)
> 
>
> **Maturity**: PROTOTYPE

### [magix/magix-rabbit](magix/magix-rabbit)
> 
>
> **Maturity**: PROTOTYPE

### [magix/magix-rsocket](magix/magix-rsocket)
> 
>
> **Maturity**: EXPERIMENTAL

### [magix/magix-server](magix/magix-server)
> 
>
> **Maturity**: EXPERIMENTAL

### [magix/magix-storage](magix/magix-storage)
> 
>
> **Maturity**: EXPERIMENTAL

### [magix/magix-zmq](magix/magix-zmq)
> 
>
> **Maturity**: EXPERIMENTAL

### [magix/magix-storage/magix-storage-xodus](magix/magix-storage/magix-storage-xodus)
> 
>
> **Maturity**: PROTOTYPE


### `demo` module

The demo includes a simple mock device with a few properties changing as `sin` and `cos` of
the current time. The device is configurable via a simple TornadoFX-based control panel.
You can run a demo by executing `application/run` Gradle task.

The graphs are displayed using [plotly.kt](https://github.com/mipt-npm/plotly.kt) library.
