# Module controls-modbus

A plugin for Controls-kt device server on top of modbus-rtu/modbus-tcp protocols

## Features

 - [modbusRegistryMap](src/main/kotlin/space/kscience/controls/modbus/ModbusRegistryMap.kt) : Type-safe modbus registry map. Allows to define both single-register and multi-register entries (using DataForge IO). 
Automatically checks consistency.
 - [modbusProcessImage](src/main/kotlin/space/kscience/controls/modbus/DeviceProcessImage.kt) : Binding of slave (server) modbus device to Controls-kt device
 - [modbusDevice](src/main/kotlin/space/kscience/controls/modbus/ModbusDevice.kt) : A device with additional methods to work with modbus registers.


## Usage

## Artifact:

The Maven coordinates of this project are `space.kscience:controls-modbus:0.3.0`.

**Gradle Kotlin DSL:**
```kotlin
repositories {
    maven("https://repo.kotlin.link")
    mavenCentral()
}

dependencies {
    implementation("space.kscience:controls-modbus:0.3.0")
}
```
