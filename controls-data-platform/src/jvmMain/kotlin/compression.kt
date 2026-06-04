package storage

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
import space.kscience.dataforge.meta.set
import space.kscience.tables.MapRow
import space.kscience.tables.Row
import space.kscience.tables.TableHeader
import space.kscience.tables.get
import kotlin.math.abs

/**
 * Represents compression settings for columnar data.
 *
 * This class is used to configure and represent the settings for compressing a column of data.
 * Column compression can be applied to optimize memory usage and processing efficiency, particularly
 * for numeric or continuous data, by skipping unchanged values or applying numeric delta encoding.
 *
 * @property skipUnchangedValues If true, unchanged values in the column will be skipped during compression.
 *                                This helps optimize storage and processing for columns with sparse changes.
 *                                Default is true.
 * @property numericDelta An optional numeric delta value. If provided, compression will encode numeric changes
 *                         by storing only the difference between successive values and this delta value.
 *                         This can reduce space for numeric data with small increments.
 */
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

/**
 * Represents the settings for compressing rows of data, providing control over which rows and values
 * should be retained, and enabling the application of numeric delta compression.
 *
 * @property skipUnchangedRows If true, rows with unchanged values compared to the previous row will be skipped.
 *                             Default is true.
 * @property skipUnchangedValues If true, individual unchanged value fields within rows will be skipped,
 *                                reducing redundancy within row data. Default is false.
 * @property numericDelta An optional numeric threshold to use for delta compression. If specified, numeric values
 *                        differing by less than this threshold will be considered unchanged.
 *                        null indicates delta compression is disabled.
 * @property columns A map of column-specific compression settings, allowing individual column behavior to
 *                   be configured independently.
 */
@Serializable
public data class RowsCompression(
    val skipUnchangedRows: Boolean = true,
    val skipUnchangedValues: Boolean = false,
    val numericDelta: Double? = null,
    val columns: Map<String, ColumnCompression> = emptyMap(),
) : MetaRepr {

    override fun toMeta(): Meta = Meta {
        "skipUnchangedRows" put skipUnchangedRows
        "skipUnchangedValues" put skipUnchangedValues
        numericDelta?.let { "numericDelta" put numericDelta }
        columns.forEach { (column, compression) ->
            set("column[$column]", compression.toMeta())
        }
    }
}

public val RowsCompression.hasCompression: Boolean get() = skipUnchangedRows || skipUnchangedValues || numericDelta != null || columns.isNotEmpty()


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
 * @param configuration Configuration that specifies compression behavior, including options
 * such as skipping unchanged rows.
 * @return A new instance of `AsyncRows` where row-level compression has been applied
 * based on the given configuration.
 */
public fun TimeSeriesRows<Meta>.compress(configuration: RowsCompression): TimeSeriesRows<Meta> {
    if (!configuration.hasCompression) return this

    // compute column configurations with defaults
    val columnConfigurations = headers.minus(timeColumnHeader).associate {
        it.name to (configuration.columns[it.name] ?: ColumnCompression(
            configuration.skipUnchangedValues,
            configuration.numericDelta
        ))
    }

    return object : TimeSeriesRows<Meta> {
        override val headers: TableHeader<Meta> = this@compress.headers

        override fun subscribe(): Flow<TimeSeriesValues<Meta>> = flow {
            var previousValues: Map<String, Meta?>? = null

            this@compress.subscribe().collect { row: TimeSeriesValues<Meta> ->
                //values except time value

                when {
                    configuration.skipUnchangedRows && row.value == previousValues -> {
                        return@collect
                    }

                    configuration.skipUnchangedValues || configuration.columns.isNotEmpty() -> {

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