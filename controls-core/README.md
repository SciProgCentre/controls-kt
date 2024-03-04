# Module controls-core

Core interfaces for building a device server

## Features

 - [device](src/commonMain/kotlin/space/kscience/controls/api/Device.kt) : Device API with subscription (asynchronous and pseudo-synchronous properties)
 - [deviceMessage](src/commonMain/kotlin/space/kscience/controls/api/DeviceMessage.kt) : Specification for messages used to communicate between Controls-kt devices.
 - [deviceHub](src/commonMain/kotlin/space/kscience/controls/api/DeviceHub.kt) : Grouping of devices into local tree-like hubs.
 - [deviceSpec](src/commonMain/kotlin/space/kscience/controls/spec) : Mechanics and type-safe builders for devices. Including separation of device specification and device state.
 - [deviceManager](src/commonMain/kotlin/space/kscience/controls/manager) : DataForge DI integration for devices. Includes device builders.
 - [ports](src/commonMain/kotlin/space/kscience/controls/ports) : Working with asynchronous data sending and receiving raw byte arrays


## Usage

## Artifact:

The Maven coordinates of this project are `space.kscience:controls-core:0.3.0`.

**Gradle Kotlin DSL:**
```kotlin
repositories {
    maven("https://repo.kotlin.link")
    mavenCentral()
}

dependencies {
    implementation("space.kscience:controls-core:0.3.0")
}
```
