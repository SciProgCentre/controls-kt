# Magix Device Client

The `deviceClient` feature provides a way to connect to remote devices via Magix as if they were local `Device` objects.

## DeviceClient

`DeviceClient` is an implementation of `CachingDevice` that communicates with a remote device by sending and receiving `DeviceMessage`s over a Magix loop.

### Key Features
- **Remote Property Access**: Read and write properties remotely.
- **Remote Action Execution**: Execute actions on the remote device.
- **Property Caching**: Automatically caches property values from `PropertyChangedMessage`s.
- **Lifecycle Management**: Tracks the remote device's lifecycle state.

## Connecting to a Remote Device

Use the `remoteDevice` extension on `MagixEndpoint`:

```kotlin
val magixEndpoint: MagixEndpoint = ...
val device: DeviceClient = magixEndpoint.remoteDevice(
    context = myContext,
    thisEndpoint = "my-client-id",
    deviceEndpoint = "remote-service-id",
    deviceName = "thermo1".parseAsName()
)
```

## Remote Device Hub

You can also discover and connect to all devices on a specific Magix endpoint using `remoteDeviceHub`. This returns a `DeviceTree` that is dynamically updated as new devices report their presence.

```kotlin
val hub: DeviceTree = magixEndpoint.remoteDeviceHub(
    context = myContext,
    thisEndpoint = "my-client-id",
    deviceEndpoint = "remote-service-id"
)
```

## Low-level Access

If you don't need a full `Device` object, you can subscribe to property flows directly:

```kotlin
val propertyFlow: Flow<Double> = magixEndpoint.controlsPropertyFlow(
    endpointName = "remote-service-id",
    deviceName = "thermo1".parseAsName(),
    propertySpec = Thermo.temperature
)
```

## Type-Safe Property Access

The `clientPropertyAccess.kt` file provides many extensions for `DeviceClient` to use `DevicePropertySpec` and `DeviceActionSpec` for type-safe interaction:

```kotlin
val temp: Double = device.read(Thermo.temperature)
device.write(Thermo.targetTemperature, 25.0)
```

## Demos and Tests

- **Tests**: `src/commonTest/kotlin/space/kscience/controls/client/RemoteDeviceConnect.kt`
- **Demos**: `demo/magix-demo`

<!-- LLM generated code: Documentation for Magix Device Client feature -->
