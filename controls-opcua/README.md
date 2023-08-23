# Module controls-opcua

A client and server connectors for OPC-UA via Eclipse Milo

## Features

 - [opcuaClient](src/main/kotlin/space/kscience/controls/opcua/client) : Connect a Controls-kt as a client to OPC UA server
 - [opcuaServer](src/main/kotlin/space/kscience/controls/opcua/server) : Create an OPC UA server on top of Controls-kt device (or device hub)


## Usage

## Artifact:

The Maven coordinates of this project are `space.kscience:controls-opcua:0.2.0`.

**Gradle Kotlin DSL:**
```kotlin
repositories {
    maven("https://repo.kotlin.link")
    //uncomment to access development builds
    //maven("https://maven.pkg.jetbrains.space/spc/p/sci/dev")
    mavenCentral()
}

dependencies {
    implementation("space.kscience:controls-opcua:0.2.0")
}
```
