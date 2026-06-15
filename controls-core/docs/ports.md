# Ports

The `ports` feature provides a common API for asynchronous data communication with hardware. It handles sending and receiving raw byte arrays or specialized phrases (messages).

## Port Types

- **AsynchronousPort**: A non-blocking port that uses a `Flow` of byte arrays to receive data.
- **SynchronousPort**: A blocking-style port that provides request-response semantics.

Common implementations include Serial ports and TCP/UDP sockets.

## Working with Ports

Example of using an `AsynchronousPort`:
```kotlin
val port = context.request(Ports).buildAsynchronousPort(meta)
port.send("HELLO".encodeToByteArray())
port.receiving().collect { bytes ->
    println("Received: ${bytes.decodeToString()}")
}
```

## Demos and Tests

- **Tests**: `../src/jvmTest/kotlin/space/kscience/controls/ports/AsynchronousPortIOTest.kt` tests asynchronous I/O operations on ports.
- **Demos**: 
    - `../../controls-modbus`: Implements Modbus protocol over ports.
    - `../../controls-serial`: Provides serial port implementation for the ports API.
    - `../../demo/mks-pdr900`: Demo for a real device using ports.

<!-- LLM generated code: Documentation for Ports feature -->
