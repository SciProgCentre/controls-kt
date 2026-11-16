# FileDeviceMessageStorage

`FileDeviceMessageStorage` is a file-based implementation of `WriteableDeviceMessageStorage` that persists
`DeviceMessage` events directly to the local filesystem without external database dependencies.

Messages are stored as line-delimited JSON (JSONL) partitioned by message type, with automatic file rotation, optional
background compression into DataForge envelopes, and time-range indexing.

---

## Key Features

- **Message Type Partitioning**: Events are segregated into separate files based on message serial type (e.g.,
  `PropertyChangedMessage-2026-10-21T10-00-00Z.jsonl`), optimizing write concurrency and type-specific queries.
- **File Rotation**: Automatically rotates to a new file when the active file reaches `maxMessagesPerFile`.
- **Background Compression (`compressHistory`)**: Rotated JSONL files can be compressed asynchronously into DataForge
  envelope (`.df`) files with ZIP/DEFLATE compression, minimizing disk space while remaining fully readable.
- **Retention Filtering (`historyDepth`)**: Files older than `historyDepth` are excluded from in-memory indices and
  query flows. Old files are preserved on disk without indexing overhead.
- **Reactive Streaming & Filtering**: Queries return Kotlin `Flow<DeviceMessage>` filtered by time range, event type,
  source device name, and target device name.
- **Fault Tolerance**: Malformed or unreadable lines emit `DeviceErrorMessage` entries in the output flow instead of
  failing the stream.

---

## Configuration Properties

| Property             | Type              | Default                               | Description                                                                     |
|----------------------|-------------------|---------------------------------------|---------------------------------------------------------------------------------|
| `directory`          | `Path` / `String` | *(Required)*                          | Directory where JSONL and envelope files are stored.                            |
| `maxMessagesPerFile` | `Int`             | `10000` (Constructor) / `1000` (Meta) | Maximum messages written to a single file before rotation.                      |
| `historyDepth`       | `Duration?`       | `null`                                | Maximum age of files to index and include in queries. `null` indexes all files. |
| `compressHistory`    | `Boolean`         | `false`                               | When `true`, compresses rotated files to `.df` envelopes in the background.     |
| `clock`              | `Clock`           | `Clock.System`                        | Clock used for file timestamping and retention checks.                          |

---

## Direct Usage

### Instantiation and Writing

```kotlin
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.storage.FileDeviceMessageStorage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.parseAsName
import java.nio.file.Path
import kotlin.time.Duration.Companion.days

val storage = FileDeviceMessageStorage(
    directory = Path.of("data/messages"),
    maxMessagesPerFile = 5000,
    historyDepth = 30.days,
    compressHistory = true
)

// Write single message
storage.write(
    PropertyChangedMessage(
        property = "temperature",
        value = Meta(23.5),
        sourceDevice = "plant.sensor1".parseAsName()
    )
)

// Write batch of messages
storage.writeAll(messagesList)
```

### Reading and Querying

```kotlin
import kotlinx.coroutines.flow.Flow
import space.kscience.controls.api.DeviceLogMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.storage.read
import space.kscience.controls.storage.propertyHistory
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.parseAsName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

val now = Clock.System.now()

// Query all messages within a time range
val recentMessages = storage.read(range = (now - 1.hours)..now)

// Query typed messages for a specific device
val sensorLogs: Flow<DeviceLogMessage> = storage.read(
    sourceDevice = "plant.sensor1".parseAsName()
)

// Property history flow adapter
val temperatureHistory = storage.propertyHistory(
    propertyName = "temperature",
    converter = MetaConverter.double,
    deviceName = "plant.sensor1".parseAsName()
)
val historyFlow = temperatureHistory.flow(from = now - 24.hours, until = now)
```

---

## Factory & Meta Configuration

`FileDeviceMessageStorage` implements DataForge `Factory<DeviceMessageStorage>` and `MetaSpec`, allowing declarative
setup via `Meta`:

```kotlin
import space.kscience.controls.storage.FileDeviceMessageStorage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.invoke
import kotlin.time.Duration.Companion.days

val storageMeta = Meta {
    FileDeviceMessageStorage.directory put "data/messages"
    FileDeviceMessageStorage.compressHistory put true
    "maxMessagesPerFile" put 2000
    "historyDepth" put 14.days
}

val storage = FileDeviceMessageStorage.build(context, storageMeta)
```

---

## Integration with DeviceTree

When using `ControlsStoragePlugin`, `FileDeviceMessageStorage` is registered as the default `"file"` message storage
factory (`MESSAGE_STORAGE_FACTORY_TARGET`). You can bind it to device trees via `DeviceTree.launchStorageJob`:

```kotlin
import space.kscience.controls.storage.launchStorageJob

deviceTree.launchStorageJob(
    storageMap = mapOf("file" to storage),
    coroutineScope = scope
)
```

---

## Lifecycle

`FileDeviceMessageStorage` implements `AutoCloseable`. Calling `close()` cancels internal background coroutines (such as
asynchronous file compression).

```kotlin
storage.close()
```
