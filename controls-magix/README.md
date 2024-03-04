# Module controls-magix

Magix service for binding controls devices (both as RPC client and server)

## Features

 - [controlsMagix](src/commonMain/kotlin/space/kscience/controls/client/controlsMagix.kt) : Connect a `DeviceManage` with one or many devices to the Magix endpoint
 - [DeviceClient](src/commonMain/kotlin/space/kscience/controls/client/DeviceClient.kt) : A remote connector to Controls-kt device via Magix


## Usage

## Artifact:

The Maven coordinates of this project are `space.kscience:controls-magix:0.3.0`.

**Gradle Kotlin DSL:**
```kotlin
repositories {
    maven("https://repo.kotlin.link")
    mavenCentral()
}

dependencies {
    implementation("space.kscience:controls-magix:0.3.0")
}
```
