# controls-magix

Magix service for binding controls devices (both as RPC client and server).

## Features

- [Magix Service](./magixService.md): Host your `DeviceManager` on Magix.
- [Remote Device Client](./deviceClient.md): Access remote devices as local `Device` objects.
- [Tango Integration](./tango.md): Bridge between Controls-kt and Tango via Magix.
- [DOOCS Integration](./doocs.md): Support for DOOCS payloads over Magix.

## Documentation and Tests
For missing features and tests, see [TODO.md](./TODO.md).

## Artifact:

The Maven coordinates of this project are `space.kscience:controls-magix:0.4.0`.

**Gradle Kotlin DSL:**
```kotlin
repositories {
    maven("https://repo.kotlin.link")
    mavenCentral()
}

dependencies {
    implementation("space.kscience:controls-magix:0.4.0")
}
```

<!-- LLM generated code: README template for controls-magix -->
