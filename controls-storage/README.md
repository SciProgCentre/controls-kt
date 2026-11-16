# Module controls-storage

An API for stand-alone Controls-kt device or a hub.

## Documentation

- [File Device Message Storage](./docs/fileDeviceMessageStorage.md): Local filesystem-based storage for `DeviceMessage` events with JSONL partitioning, automatic rotation, background ZIP/DEFLATE compression, and reactive querying.


## Usage

## Artifact:

The Maven coordinates of this project are `space.kscience:controls-storage:0.5.0-dev-6`.

**Gradle Kotlin DSL:**
```kotlin
repositories {
    maven("https://repo.kotlin.link")
    mavenCentral()
}

dependencies {
    implementation("space.kscience:controls-storage:0.5.0-dev-6")
}
```
