# Tango Magix Integration

The `tango` feature provides integration with [Tango Controls](https://www.tango-controls.org/) via the [Tango-flavored Magix loop](https://github.com/waltz-controls/rfc/tree/master/4).

## Tango Magix Service

You can expose your Controls-kt devices to a Tango-compatible Magix loop using `launchTangoMagix`:

```kotlin
val deviceManager: DeviceManager = ...
val magixEndpoint: MagixEndpoint = ...

deviceManager.launchTangoMagix(magixEndpoint, endpointID = "tango")
```

### Supported Actions
The service supports the following Tango actions:
- **read**: Maps to reading a device property.
- **write**: Maps to writing a device property.
- **exec**: Maps to executing a device action.

## Tango Payloads

The integration uses specialized payload structures defined in `TangoPayload`. These payloads include:
- `host`: The Tango host.
- `device`: The Tango device name (parsed as a Controls-kt `Name`).
- `name`: The attribute or command name.
- `value`, `argin`, `argout`: Data associated with the action.
- `quality`: Data quality (VALID, WARNING, ALARM).

## Demos and Tests

- **Demos**: `demo/magix-demo` (Check if there is a specific Tango demo)

<!-- LLM generated code: Documentation for Tango Magix feature -->
