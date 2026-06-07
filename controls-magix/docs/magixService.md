# Magix Service

The `magixService` feature allows hosting a `DeviceManager` (and all its devices) on a [Magix](https://github.com/waltz-controls/magix) endpoint. This enables remote access to the devices using the standard Controls-kt message format.

## Launching the Service

To start the Magix service, use the `launchMagixService` extension on `DeviceManager`:

```kotlin
val deviceManager: DeviceManager = ...
val magixEndpoint: MagixEndpoint = ...

deviceManager.launchMagixService(magixEndpoint, endpointID = "my-cool-endpoint")
```

### Parameters
- **endpoint**: The `MagixEndpoint` to connect to.
- **endpointID**: A unique identifier for this service on the Magix loop.
- **coroutineContext**: (Optional) Additional coroutine context for the service.

## How it Works

1. **Inbound Messages**: The service subscribes to Magix messages with a format of `controls-kt`. It filters messages targeted at `endpointID` or broadcast messages.
2. **Device Interaction**: When a message is received, it is passed to the `DeviceManager.respondMessage` function, which routes it to the appropriate device.
3. **Outbound Responses**: Any responses from devices (like property values or action results) are sent back to the Magix loop, targeted at the original requester.
4. **Autonomous Messages**: The service also listens to the `DeviceManager.messageFlow()` and broadcasts any autonomous messages (like `PropertyChangedMessage` from hardware changes) to the Magix loop.

## Message Format

The service uses a specialized `MagixFormat` for `DeviceMessage`:

```kotlin
public val DeviceManager.Companion.magixFormat: MagixFormat<DeviceMessage>
```

This format uses Kotlinx Serialization to encode `DeviceMessage` objects as JSON.

## Demos and Tests

- **Tests**: `src/jvmTest/kotlin/space/kscience/controls/client/MagixLoopTest.kt`
- **Demos**: `demo/magix-demo`

<!-- LLM generated code: Documentation for Magix Service feature -->
