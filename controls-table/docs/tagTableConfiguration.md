# TagTable Configuration JSON Specification

`TagTableConfiguration` provides a declarative, serializable specification for defining a data platform (tag table). It
defines external industrial data sources (OPC UA, Modbus TCP/RTU, Apache PLC4X, and controls devices), sampling timers,
individual tag properties with address mappings and quality thresholds, binary file persistence settings, and event
message storage.

This document describes the JSON schema used to serialize and deserialize `TagTableConfiguration`. It serves as a
reference for authoring tag table configurations manually, integrating with external systems, or generating
configurations programmatically.

---

## Top-Level Structure

A tag table configuration JSON document represents a single `TagTableConfiguration` root object:

```json
{
  "sources": {},
  "timers": {},
  "tags": {},
  "storage": null,
  "messageStorage": null
}
```

### Fields

| Field            | Type     | Required | Default | Description                                                                           |
|------------------|----------|----------|---------|---------------------------------------------------------------------------------------|
| `sources`        | `object` | Yes      | `{}`    | Map of source identifiers to `TagSourceConfig` definitions.                           |
| `timers`         | `object` | Yes      | `{}`    | Map of timer identifiers to `TimerConfiguration` polling schedules.                   |
| `tags`           | `object` | Yes      | `{}`    | Map of tag identifiers (column names) to `TagTableColumn` configurations.             |
| `storage`        | `object` | No       | `null`  | Optional `TagTableStorageConfiguration` managing binary time-series file persistence. |
| `messageStorage` | `object` | No       | `null`  | Optional `MessageStorageConfiguration` managing non-data message storage.             |

---

## Data Sources (`TagSourceConfig`)

The `sources` map specifies external protocol connections. Each source is polymorphic and identified by its `"type"`
property.

> **Note**: Properties can also read directly from internal `controls-kt` devices registered in `DeviceManager` using
> the reserved source name `"@controls"`, which requires no entry in `sources`.

### 1. OPC UA (`"type": "opc"`)

Connects to an OPC UA server using Eclipse Milo.

```json
"opcServer": {
  "type": "opc",
  "host": "opc.tcp://192.168.1.50:4840",
  "maxAge": "PT0.5S",
  "reconnectionDelay": "PT5S"
}
```

| Field               | Type     | Required | Default    | Description                                                     |
|---------------------|----------|----------|------------|-----------------------------------------------------------------|
| `type`              | `string` | Yes      | `"opc"`    | Protocol discriminator.                                         |
| `host`              | `string` | Yes      | —          | Host connection endpoint URL (e.g. `opc.tcp://localhost:4840`). |
| `maxAge`            | `string` | No       | `"PT0.5S"` | Maximum age of cached values returned by the server (ISO-8601). |
| `reconnectionDelay` | `string` | No       | `"PT5S"`   | Delay between reconnection attempts after connection failure.   |

### 2. Modbus TCP (`"type": "modbus-tcp"`)

Connects to a Modbus TCP server/gateway over Ethernet.

```json
"modbusTcpSource": {
  "type": "modbus-tcp",
  "addr": "192.168.1.100",
  "port": 502,
  "timeout": 3000,
  "reconnect": true,
  "reconnectionDelay": "PT5S"
}
```

| Field               | Type      | Required | Default        | Description                                                   |
|---------------------|-----------|----------|----------------|---------------------------------------------------------------|
| `type`              | `string`  | Yes      | `"modbus-tcp"` | Protocol discriminator.                                       |
| `addr`              | `string`  | Yes      | —              | Remote IP address or hostname of the Modbus TCP server.       |
| `port`              | `integer` | No       | `502`          | TCP port number.                                              |
| `timeout`           | `integer` | No       | `3000`         | Request socket timeout in milliseconds.                       |
| `reconnect`         | `boolean` | No       | `false`        | Whether to maintain a persistent connection or reconnect.     |
| `reconnectionDelay` | `string`  | No       | `"PT5S"`       | Delay between reconnection attempts after connection failure. |

### 3. Modbus RTU (`"type": "modbus-rtu"`)

Communicates with Modbus RTU devices via a local serial port.

```json
"modbusRtuSource": {
  "type": "modbus-rtu",
  "portName": "COM3",
  "baudRate": 19200,
  "databits": 8,
  "stopbits": 1,
  "parity": 0,
  "echo": false,
  "flowControlIn": 0,
  "flowControlOut": 0,
  "timeout": 3000,
  "transmitDelay": 0,
  "reconnectionDelay": "PT5S"
}
```

| Field               | Type      | Required | Default        | Description                                                         |
|---------------------|-----------|----------|----------------|---------------------------------------------------------------------|
| `type`              | `string`  | Yes      | `"modbus-rtu"` | Protocol discriminator.                                             |
| `portName`          | `string`  | Yes      | —              | Serial port name (e.g. `COM1` on Windows, `/dev/ttyUSB0` on Linux). |
| `baudRate`          | `integer` | No       | `9600`         | Baud rate (e.g. `9600`, `19200`, `115200`).                         |
| `databits`          | `integer` | No       | `8`            | Number of data bits (usually `8`).                                  |
| `stopbits`          | `integer` | No       | `1`            | Stop bits (`1` = ONE_STOP_BIT, `2` = TWO_STOP_BITS).                |
| `parity`            | `integer` | No       | `0`            | Parity (`0` = NO_PARITY, `1` = ODD, `2` = EVEN).                    |
| `echo`              | `boolean` | No       | `false`        | Enable or disable echo.                                             |
| `flowControlIn`     | `integer` | No       | `0`            | Input flow control (`0` = disabled).                                |
| `flowControlOut`    | `integer` | No       | `0`            | Output flow control (`0` = disabled).                               |
| `timeout`           | `integer` | No       | `3000`         | Response timeout in milliseconds.                                   |
| `transmitDelay`     | `integer` | No       | `0`            | Inter-transmission delay in milliseconds.                           |
| `reconnectionDelay` | `string`  | No       | `"PT5S"`       | Reconnection delay duration.                                        |

### 4. Apache PLC4X (`"type": "plc4x"`)

Connects to PLCs using Apache PLC4X driver URIs (e.g. Siemens S7, Modbus, Beckhoff ADS, Ethernet/IP).

```json
"plcSource": {
  "type": "plc4x",
  "address": "s7://192.168.0.1",
  "reconnectionDelay": "PT5S"
}
```

| Field               | Type     | Required | Default   | Description                                                   |
|---------------------|----------|----------|-----------|---------------------------------------------------------------|
| `type`              | `string` | Yes      | `"plc4x"` | Protocol discriminator.                                       |
| `address`           | `string` | Yes      | —         | PLC4X connection URI (e.g. `s7://192.168.0.1`, `ads://...`).  |
| `reconnectionDelay` | `string` | No       | `"PT5S"`  | Delay between reconnection attempts after connection failure. |

---

## Timers (`TimerConfiguration`)

The `timers` map defines polling schedules for reading tags. Each timer configuration is polymorphic and identified by
`"type"`.

### Fixed-Rate Timer (`"type": "fixed-rate"`)

Fires periodically at a constant time interval.

```json
"timers": {
  "fastScan": {
    "type": "fixed-rate",
    "tick": "PT0.1S"
  },
  "slowScan": {
    "type": "fixed-rate",
    "tick": "PT1S"
  }
}
```

| Field  | Type     | Required | Description                                                                                |
|--------|----------|----------|--------------------------------------------------------------------------------------------|
| `type` | `string` | Yes      | `"fixed-rate"`.                                                                            |
| `tick` | `string` | Yes      | Polling interval encoded as an ISO-8601 duration (e.g. `PT0.1S` for 100ms, `PT1S` for 1s). |

---

## Tag Columns (`TagTableColumn`)

The `tags` map defines each individual data point (column) in the tag table.

```json
"tags": {
  "temperature": {
    "source": "opcServer",
    "timer": "fastScan",
    "config": {
      "nodeId": "ns=2;s=Sensors.Temperature"
    },
    "timeout": "PT1S",
    "compression": {
      "skipUnchangedValues": true,
      "numericDelta": 0.1
    },
    "removeAuxiliaryValues": true,
    "maxAge": "PT10S",
    "maxDelta": 5.0,
    "meta": {
      "description": "Reactor Core Temperature"
    }
  }
}
```

### TagTableColumn Fields

| Field                   | Type      | Required | Default  | Description                                                                                        |
|-------------------------|-----------|----------|----------|----------------------------------------------------------------------------------------------------|
| `source`                | `string`  | Yes      | —        | Identifier of the data source declared in `sources`, or `"@controls"` for controls devices.        |
| `timer`                 | `string`  | Yes      | —        | Identifier of the timer declared in `timers`.                                                      |
| `config`                | `object`  | Yes      | —        | Source-specific address and register configuration (see below).                                    |
| `timeout`               | `string`  | No       | `"PT1S"` | Read timeout for the property as an ISO-8601 duration.                                             |
| `compression`           | `object`  | No       | `null`   | Optional column-specific compression settings (see [ColumnCompression](#columncompression)).       |
| `removeAuxiliaryValues` | `boolean` | No       | `true`   | Whether to strip protocol-specific auxiliary metadata (e.g. `@opc` parameters) from stored values. |
| `maxAge`                | `string`  | No       | `null`   | Maximum allowed age of the value for `GOOD` quality status (ISO-8601 duration).                    |
| `maxDelta`              | `number`  | No       | `null`   | Maximum acceptable change threshold relative to the previous sample for `GOOD` quality status.     |
| `meta`                  | `object`  | No       | `{}`     | Arbitrary user or application metadata dictionary attached to this column.                         |

---

### Source-Specific `config` Object Formats

The `config` object specifies addressing according to the protocol used by the referenced `source`:

#### 1. OPC UA Configuration

Used when `source` points to an OPC UA source.

```json
"config": {
  "nodeId": "ns=2;s=Device.Sensor.Temperature"
}
```

* `nodeId` (`string`, required): Standard OPC UA NodeId string (e.g. `ns=2;s=Temperature`, `ns=1;i=1001`).

#### 2. Modbus Configuration

Used when `source` points to a Modbus TCP or RTU source.

```json
"config": {
  "address": 100,
  "unitId": 1,
  "reader": "holding-float"
}
```

* `address` (`integer`, required): Modbus register address (0-based or 1-based per device convention).
* `unitId` (`integer`, optional, default `1`): Modbus slave/unit ID.
* `reader` (`string`, required): Modbus register reader type:
    * `"double"`: Read 64-bit IEEE 754 double from 4 input registers.
    * `"float"`: Read 32-bit IEEE 754 float from 2 input registers (big-endian).
    * `"short"`: Read 16-bit signed integer from 1 input register.
    * `"holding-double"`: Read 64-bit double from 4 holding registers.
    * `"holding-float"`: Read 32-bit float from 2 holding registers.
    * `"holding-short"`: Read 16-bit signed integer from 1 holding register.
    * `"coil"`: Read single boolean coil.
    * `"discrete"`: Read single boolean discrete input.

#### 3. Apache PLC4X Configuration

Used when `source` points to a PLC4X source.

```json
"config": {
  "address": "%DB1.DBD0",
  "plcValueType": "REAL",
  "name": "speed"
}
```

* `address` (`string`, required): PLC field address expression (e.g. `%DB1.DBD0`, `%M0.0`, `%I0.1`).
* `plcValueType` (`string`, required): Target data type name from `PlcValueType` enum (`"REAL"`, `"LREAL"`, `"INT"`,
  `"DINT"`, `"BOOL"`, `"WORD"`, `"STRING"`, etc.).
* `name` (`string`, optional): Field name alias (defaults to `"@default"`).

#### 4. Controls Device Configuration (`"@controls"`)

Used when `source` is `"@controls"` to read directly from a device in `DeviceManager`.

```json
"config": {
  "deviceName": "generator",
  "propertyName": "voltage"
}
```

* `deviceName` (`string`, required): Name of the device registered in `DeviceManager`.
* `propertyName` (`string`, required): Property name on the device.

---

## Storage Configuration (`TagTableStorageConfiguration`)

The optional `storage` object configures high-throughput interval-indexed binary envelope storage.

```json
"storage": {
  "path": "data/storage",
  "readInterval": "PT0.1S",
  "withTagState": false,
  "writeOldValues": true,
  "maxRowsPerEnvelope": 10000,
  "maxDuration": "PT3H",
  "maxPause": null,
  "compression": {
    "skipUnchangedRows": true,
    "skipUnchangedValues": false,
    "numericDelta": 0.05,
    "columns": {}
  },
  "splitStrategy": {
    "type": "byDate",
    "timeZoneId": "UTC"
  },
  "rowsConverterType": "controls.table.zip",
  "separateMeta": true
}
```

### Storage Fields

| Field                | Type      | Required | Default                | Description                                                                                     |
|----------------------|-----------|----------|------------------------|-------------------------------------------------------------------------------------------------|
| `path`               | `string`  | Yes      | —                      | Filesystem directory where binary data envelopes are stored.                                    |
| `readInterval`       | `string`  | Yes      | —                      | Interval at which table rows are polled and batched into storage envelopes (ISO-8601 duration). |
| `withTagState`       | `boolean` | No       | `false`                | Include diagnostic tag quality columns (`<tag>.@quality`) in storage.                           |
| `writeOldValues`     | `boolean` | No       | `true`                 | Whether previously read unchanged values are recorded in every stored row.                      |
| `maxRowsPerEnvelope` | `integer` | No       | `10000`                | Maximum number of rows contained in a single binary envelope file.                              |
| `maxDuration`        | `string`  | No       | `"PT3H"`               | Maximum time span of a single envelope before rotating to a new file (ISO-8601 duration).       |
| `maxPause`           | `string`  | No       | `null`                 | Maximum allowable inactivity pause before closing the current envelope file.                    |
| `compression`        | `object`  | No       | `null`                 | Row and column level compression settings (see [RowsCompression](#rowscompression)).            |
| `splitStrategy`      | `object`  | No       | `{"type": "byDate"}`   | Directory partitioning strategy (`"byDate"`, `"byHour"`, or `"flat"`).                          |
| `rowsConverterType`  | `string`  | No       | `"controls.table.zip"` | Binary envelope format encoder (e.g. `"controls.table.zip"`).                                   |
| `separateMeta`       | `boolean` | No       | `true`                 | Store envelope metadata in a separate `.json` file alongside the binary container.              |

### Compression Configuration

#### `ColumnCompression`

* `skipUnchangedValues` (`boolean`, default `true`): Omit duplicate values when unchanged from previous row.
* `numericDelta` (`number?`, optional): Numeric difference threshold below which changes are considered insignificant.

#### `RowsCompression`

* `skipUnchangedRows` (`boolean`, default `true`): Omit entire row if all values are unchanged.
* `skipUnchangedValues` (`boolean`, default `false`): Omit individual unchanged value fields within rows.
* `numericDelta` (`number?`, optional): Global numeric delta compression threshold.
* `columns` (`object`, default `{}`): Map of column names to column-specific `ColumnCompression` configurations.

### Directory Split Strategies (`DataPlatformFileSplit`)

* **`byDate`**: Organizes data in `YYYY/MM/DD` directory trees.
  ```json
  "splitStrategy": {
    "type": "byDate",
    "timeZoneId": "UTC"
  }
  ```
* **`byHour`**: Organizes data in `YYYY/MM/DD/HH` directory trees.
  ```json
  "splitStrategy": {
    "type": "byHour",
    "timeZoneId": "UTC"
  }
  ```
* **`flat`**: Stores all envelope files in the root storage directory without subfolders.
  ```json
  "splitStrategy": {
    "type": "flat"
  }
  ```

---

## Message Storage Configuration (`MessageStorageConfiguration`)

The optional `messageStorage` object configures storage for non-data event and lifecycle messages.

```json
"messageStorage": {
  "path": "data/messages",
  "sourceDevicePrefixFilter": "@plcTagTable",
  "maxMessagesPerFile": 10000
}
```

| Field                      | Type      | Required | Default          | Description                                                       |
|----------------------------|-----------|----------|------------------|-------------------------------------------------------------------|
| `path`                     | `string`  | Yes      | —                | Filesystem directory where message files are stored.              |
| `sourceDevicePrefixFilter` | `string`  | No       | `"@plcTagTable"` | Device name prefix filter for capturing matching device messages. |
| `maxMessagesPerFile`       | `integer` | No       | `10000`          | Maximum number of messages stored per file before rotating.       |

---

## Complete Example

Below is a complete `TagTableConfiguration` JSON demonstrating multi-protocol ingestion, timers, compression, and
storage:

```json
{
  "sources": {
    "opcServer": {
      "type": "opc",
      "host": "opc.tcp://192.168.1.50:4840",
      "maxAge": "PT0.5S",
      "reconnectionDelay": "PT5S"
    },
    "modbusPump": {
      "type": "modbus-tcp",
      "addr": "192.168.1.100",
      "port": 502,
      "timeout": 3000,
      "reconnect": true,
      "reconnectionDelay": "PT5S"
    },
    "plcMain": {
      "type": "plc4x",
      "address": "s7://192.168.1.10",
      "reconnectionDelay": "PT5S"
    }
  },
  "timers": {
    "fastScan": {
      "type": "fixed-rate",
      "tick": "PT0.1S"
    },
    "slowScan": {
      "type": "fixed-rate",
      "tick": "PT1S"
    }
  },
  "tags": {
    "reactorTemperature": {
      "source": "opcServer",
      "timer": "fastScan",
      "config": {
        "nodeId": "ns=2;s=Reactor.Temperature"
      },
      "timeout": "PT1S",
      "compression": {
        "skipUnchangedValues": true,
        "numericDelta": 0.05
      },
      "removeAuxiliaryValues": true,
      "maxAge": "PT10S",
      "maxDelta": 5.0,
      "meta": {
        "description": "Primary reactor temperature"
      }
    },
    "pumpPressure": {
      "source": "modbusPump",
      "timer": "slowScan",
      "config": {
        "address": 100,
        "unitId": 1,
        "reader": "holding-float"
      },
      "timeout": "PT1S",
    },
    "conveyorSpeed": {
      "source": "plcMain",
      "timer": "fastScan",
      "config": {
        "address": "%DB1.DBD0",
        "plcValueType": "REAL",
        "name": "speed"
      },
      "timeout": "PT1S"
    },
    "virtualGeneratorVoltage": {
      "source": "@controls",
      "timer": "fastScan",
      "config": {
        "deviceName": "generator",
        "propertyName": "voltage"
      }
    }
  },
  "storage": {
    "path": "data/storage",
    "readInterval": "PT0.1S",
    "withTagState": true,
    "writeOldValues": true,
    "maxRowsPerEnvelope": 10000,
    "maxDuration": "PT3H",
    "compression": {
      "skipUnchangedRows": true,
      "skipUnchangedValues": false,
      "numericDelta": 0.01
    },
    "splitStrategy": {
      "type": "byDate",
      "timeZoneId": "UTC"
    },
    "rowsConverterType": "controls.table.zip",
    "separateMeta": true
  },
  "messageStorage": {
    "path": "data/messages",
    "sourceDevicePrefixFilter": "@plcTagTable",
    "maxMessagesPerFile": 10000
  }
}
```

---

## Kotlin Usage

### Loading Configuration from JSON and Installing

```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import space.kscience.controls.tagtable.TagTableConfiguration
import space.kscience.controls.tagtable.TagTablePlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import java.io.InputStream

fun installTagTableFromJson(context: Context, inputStream: InputStream) {
    val json = Json {
        ignoreUnknownKeys = true
    }
    val configuration = json.decodeFromStream<TagTableConfiguration>(inputStream)
    val tagTablePlugin = context.request(TagTablePlugin)
    val tagTable = tagTablePlugin.install(configuration)
}
```

### Serializing Configuration to JSON

```kotlin
import kotlinx.serialization.json.Json
import space.kscience.controls.tagtable.TagTableConfiguration

val json = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

val jsonString = json.encodeToString(TagTableConfiguration.serializer(), configuration)
```

<!-- LLM generated code: Documentation for TagTableConfiguration JSON structure -->
