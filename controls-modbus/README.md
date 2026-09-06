# Module controls-modbus

A plugin for Controls-kt device server on top of modbus-rtu/modbus-tcp protocols

## Documentation

- [Modbus Registry Map](./docs/registryMap.md): Type-safe Modbus registry maps, typed keys, multi-register ranges via `IOFormat`, validation, JSON export, and master operations.
- [Device Process Image Binding](./docs/processImage.md): Binding `controls-kt` devices to Modbus slave (server) process images with two-way property synchronization and action triggers.

## Features

 - [modbusRegistryMap](src/main/kotlin/space/kscience/controls/modbus/ModbusRegistryMap.kt) : Type-safe modbus registry map. Allows to define both single-register and multi-register entries (using DataForge IO). 
Automatically checks consistency.
 - [modbusProcessImage](src/main/kotlin/space/kscience/controls/modbus/DeviceProcessImage.kt) : Binding of slave (server) modbus device to Controls-kt device


## Usage

## Artifact:

The Maven coordinates of this project are `space.kscience:controls-modbus:0.5.0-dev`.

**Gradle Kotlin DSL:**
```kotlin
repositories {
    maven("https://repo.kotlin.link")
    mavenCentral()
}

dependencies {
    implementation("space.kscience:controls-modbus:0.5.0-dev")
}
```

<!-- LLM generated code: README-TEMPLATE for controls-modbus -->
