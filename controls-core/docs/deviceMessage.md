# Device Message

`DeviceMessage` is the common specification for communication between devices in `Controls-kt`. All interaction, whether local or remote, is eventually translated into these messages.

## Message Types

The library defines several standard message types:

- **Property Get/Set**: `PropertyGetMessage` and `PropertySetMessage` for requesting or changing property values.
- **Property Change Notification**: `PropertyChangedMessage` sent by the device when its state changes.
- **Action Execution**: `ActionExecuteMessage` and `ActionResultMessage` for calling and getting results from device actions.
- **Description**: `GetDescriptionMessage` and `DescriptionMessage` for discovering device capabilities.
- **Log and Error**: `DeviceLogMessage` and `DeviceErrorMessage` for reporting status and issues.
- **Lifecycle**: `DeviceLifeCycleMessage` for tracking device state changes.

## Working with Messages

Messages are `Serializable` using `kotlinx.serialization` and can be easily converted to/from JSON or DataForge `Meta`.

Example of creating a message:
```kotlin
val message = PropertyChangedMessage(
    property = "temperature",
    value = 25.5.asMeta(),
    sourceDevice = "sensor".asName(),
    time = Clock.System.now()
)
```

## Demos and Tests

- **Tests**: `../src/commonTest/kotlin/space/kscience/controls/api/MessageTest.kt` contains tests for message serialization and handling.
- **Demos**: 
    - `../../demo/magix-demo`: Shows how messages are used for remote communication through the Magix broker.
    - `../../demo/echo`: A simple demo showing message exchange.

<!-- LLM generated code: Documentation for DeviceMessage feature -->
