package space.kscience.controls.dataplatform.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import space.kscience.controls.dataplatform.DataPlatform.Companion.timeColumnHeader
import space.kscience.controls.dataplatform.timeseries.TimeSeriesRows
import space.kscience.controls.dataplatform.timeseries.TimeSeriesValues
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaRepr
import space.kscience.dataforge.meta.double
import space.kscience.tables.MapRow
import space.kscience.tables.Row
import space.kscience.tables.TableHeader
import space.kscience.tables.get
import kotlin.math.abs


@Serializable
public data class ColumnCompression(
    val skipUnchangedValues: Boolean = true,
    val numericDelta: Double? = null
) : MetaRepr {

    override fun toMeta(): Meta = Meta {
        "skipUnchangedValues" put skipUnchangedValues
        numericDelta?.let { "numericDelta" put numericDelta }
    }
}

@Serializable
public data class RowsCompression(
    val skipUnchangedRows: Boolean = true,
    val skipUnchangedValues: Boolean = false,
    val numericDelta: Double? = null,
) : MetaRepr {

    override fun toMeta(): Meta = Meta {
        "skipUnchangedRows" put skipUnchangedRows
        "skipUnchangedValues" put skipUnchangedValues
        numericDelta?.let { "numericDelta" put numericDelta }
    }
}

public val RowsCompression.hasCompression: Boolean get() = skipUnchangedRows || skipUnchangedValues || numericDelta != null


private fun Row<Meta>.toMap(header: TableHeader<Meta>): Map<String, Meta?> = if (this is MapRow) {
    this.values
} else {
    header.associate { it.name to get(it) }
}

/**
 * Compresses the rows of the current asynchronous dataset based on the provided configuration.
 *
 * This method applies row-level compression to reduce redundancy in the dataset. For instance,
 * if `skipUnchangedRows` in the configuration is enabled, consecutive rows with identical
 * data are omitted from the emitted flow.
 *
 * @param rowsCompression Configuration that specifies compression behavior, including options
 * such as skipping unchanged rows.
 * @param columnCompression Optional configuration for column-level compression
 * @return A new instance of `AsyncRows` where row-level compression has been applied
 * based on the given configuration.
 */
public fun TimeSeriesRows<Meta>.compress(
    rowsCompression: RowsCompression,
    columnCompression: Map<String, ColumnCompression> = emptyMap(),
): TimeSeriesRows<Meta> {
    if (!rowsCompression.hasCompression && columnCompression.isEmpty()) return this

    // compute column configurations with defaults
    val columnConfigurations = headers.minus(timeColumnHeader).associate {
        it.name to (columnCompression[it.name] ?: ColumnCompression(
            rowsCompression.skipUnchangedValues,
            rowsCompression.numericDelta
        ))
    }

    return object : TimeSeriesRows<Meta> {
        override val headers: TableHeader<Meta> = this@compress.headers

        override fun subscribe(): Flow<TimeSeriesValues<Meta>> = flow {
            var previousValues: Map<String, Meta?>? = null

            this@compress.subscribe().collect { row: TimeSeriesValues<Meta> ->
                //values except time value

                when {
                    rowsCompression.skipUnchangedRows && row.value == previousValues -> {
                        return@collect
                    }

                    rowsCompression.skipUnchangedValues || columnCompression.isNotEmpty() -> {

                        val changedValues = row.value.filter { (key, value) ->
                            //if the field is unknown, skip it just in case
                            val config = columnConfigurations[key] ?: return@filter true
                            //if value is the same, keep it only if filtering is off
                            if (value == previousValues?.get(key)) return@filter !config.skipUnchangedValues

                            //if numeric delta is specified, check it
                            if (config.numericDelta != null) {
                                // if current or previous value does not exist, skip
                                val previousNumeric = previousValues?.get(key)?.double ?: return@filter true
                                val numeric = value.double ?: return@filter true
                                abs(numeric - previousNumeric) > config.numericDelta
                            } else {
                                true
                            }
                        }

                        previousValues = (previousValues ?: emptyMap()) + changedValues

                        emit(ValueWithTime(changedValues, row.time))
                    }

                    else -> {
                        emit(row)
                        previousValues = row.value
                    }
                }
            }
        }
    }
}