# AsyncRows Compression

The `AsyncRows.compress` functionality is designed to reduce the volume of data emitted by a device by filtering out redundant information.

## Compression Logic

Compression is configured using `RowsCompression` and `ColumnCompression` classes.

### Row-level Compression

*   **Skip Unchanged Rows**: If `skipUnchangedRows` is set to `true`, a row is completely skipped if all of its values (excluding the time column) are identical to the previous row.

### Column-level Compression

Column-level compression allows for more granular control over which values are emitted.

*   **Skip Unchanged Values**: When enabled (either globally via `RowsCompression.skipUnchangedValues` or per-column via `ColumnCompression.skipUnchangedValues`), a value for a specific column is omitted from the emitted row if it is identical to the value in the previous row.
*   **Numeric Delta**: If `numericDelta` is specified for a column, a value is considered "unchanged" if the absolute difference between the current value and the previously emitted value is less than or equal to the delta.

### Partial Rows

When column-level compression is active, emitted rows may be "partial" (i.e., they only contain the `time` column and the columns that have actually changed). 

## Data Format

The compression results in `AsyncRows` where:
1. Some rows might be missing (row-level skipping).
2. Rows might contain fewer columns than defined in the headers (value-level skipping).
3. The `time` column is always preserved for every emitted row.

## Configuration Example

```kotlin
val compression = RowsCompression(
    skipUnchangedRows = true,
    columns = mapOf(
        "temperature" to ColumnCompression(numericDelta = 0.5),
        "status" to ColumnCompression(skipUnchangedValues = true)
    )
)

val compressedRows = device.asyncRows().compress(compression)
```

## JSON Representation

### Configuration JSON

The `RowsCompression` and `ColumnCompression` classes are `@Serializable`, allowing them to be easily converted to JSON for storage or transmission.

```json
{
  "skipUnchangedRows": true,
  "skipUnchangedValues": false,
  "columns": {
    "temperature": {
      "skipUnchangedValues": true,
      "numericDelta": 0.5
    },
    "status": {
      "skipUnchangedValues": true,
      "numericDelta": null
    }
  }
}
```

### Data JSON

When rows are emitted after compression, they are represented as `MapRow<Meta>`. When serialized to JSON, only the changed columns and the `@time` column are included.

Suppose we have the following sequence of raw data:
1. `{"@time": "2023-05-24T10:00:00Z", "temperature": 20.0, "status": "OK"}`
2. `{"@time": "2023-05-24T10:00:01Z", "temperature": 20.1, "status": "OK"}`
3. `{"@time": "2023-05-24T10:00:02Z", "temperature": 20.7, "status": "OK"}`

With the configuration above (`numericDelta` for temperature is 0.5, `skipUnchangedValues` for status is true), the emitted JSON would look like:

```json
[
  {
    "@time": "2023-05-24T10:00:00Z",
    "temperature": 20.0,
    "status": "OK"
  },
  {
    "@time": "2023-05-24T10:00:02Z",
    "temperature": 20.7
  }
]
```

*   The second raw row is skipped entirely because `temperature` changed by only 0.1 (less than `numericDelta` 0.5) and `status` remained unchanged.
*   In the third row, `status` is omitted because it hasn't changed since the first row. `temperature` is included because it changed by 0.7 from the last emitted value (20.0).
