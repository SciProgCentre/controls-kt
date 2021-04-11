[![JetBrains Research](https://jb.gg/badges/research.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

# DataForge-control

DataForge-control is a data acquisition framework (work in progress). It is based on DataForge, a software framework for automated data processing.
This repository contains a prototype of API and simple implementation 
of a slow control system, including a demo.

DataForge-control uses some concepts and modules of DataForge, 
such as `Meta` (immutable tree-like structure) and `MetaItem` (which 
includes a scalar value, or a tree of values, easily convertable to/from JSON 
if needed).  

To learn more about DataForge, please consult the following URLs:
 * [Kotlin multiplatform implementation of DataForge](https://github.com/mipt-npm/dataforge-core)  
 * [DataForge documentation](http://npm.mipt.ru/dataforge/) 
 * [Original implementation of DataForge](https://bitbucket.org/Altavir/dataforge/src/default/)

DataForge-control is a [Kotlin-multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html)
application. Asynchronous operations are implemented with 
[kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) library.


### Features
Among other things, you can:
- Describe devices and their properties. 
- Collect data from devices and execute arbitrary actions supported by a device.
- Property values can be cached in the system and requested from devices as needed, asynchronously.
- Connect devices to event bus via bidirectional message flows.

### `dataforge-control-core` module packages

- `api` - defines API for device management. The main class here is 
[`Device`](controls-core/src/commonMain/kotlin/ru/mipt/npm/controls/api/Device.kt).
Generally, a Device has Properties that can be read and written. Also, some Actions
can optionally be applied on a device (may or may not affect properties). 

- `base` - contains baseline `Device` implementation 
[`DeviceBase`](dataforge-device-core/src/commonMain/kotlin/hep/dataforge/control/base/DeviceBase.kt)
and property implementation, including property asynchronous flows.

- `controllers` - implements Message Controller that can be attached to the event bus, Message 
and Property flows.

### `demo` module

The demo includes a simple mock device with a few properties changing as `sin` and `cos` of
the current time. The device is configurable via a simple TornadoFX-based control panel. 
You can run a demo by executing `application/run` Gradle task. 

The graphs are displayed using [plotly.kt](https://github.com/mipt-npm/plotly.kt) library.

Example view of a demo:

![](docs/pictures/demo-view.png)
