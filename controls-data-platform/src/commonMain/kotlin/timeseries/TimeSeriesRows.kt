package space.kscience.controls.dataplatform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import space.kscience.attributes.SafeType
import space.kscience.attributes.safeTypeOf
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MetaReader
import space.kscience.dataforge.meta.Value
import space.kscience.tables.*

public typealias TimeSeriesValues<T> = ValueWithTime<Map<String, T>>

internal fun <T> TimeSeriesValues<T>.toRow(): Row<T> = MapRow(value)

public interface TimeSeriesRows<T> {
    /**
     * An ordered list of headers that *must* be present.
     */
    public val headers: TableHeader<T>

    /**
     * A dynamic flow of rows
     */
    public fun rowFlow(): Flow<TimeSeriesValues<T>>
}

/**
 * Collect [rowNum] rows from the source and represent them as a table
 */
public suspend fun <T> TimeSeriesRows<T>.collectTable(rowNum: Int): RowTable<T> {
    val rows = rowFlow().map {
        it.toRow()
    }.take(rowNum).toList()

    return RowTable(headers, rows)
}

/**
 * Transforms an `AsyncRows` instance containing metadata rows into an `AsyncRows` instance with rows of a different type
 * by applying the provided `MetaReader`.
 *
 * @param reader The `MetaReader` used to transform metadata into the specified `valueType`.
 * @param valueType The `SafeType` representing the type of rows in the resulting `AsyncRows`.
 * @return An instance of `AsyncRows` containing rows of the specified type, obtained by applying the transformation defined by `reader`.
 */
public fun <T> TimeSeriesRows<Meta>.readWith(
    reader: MetaReader<T>,
    valueType: SafeType<T>
): TimeSeriesRows<T> = object : TimeSeriesRows<T> {
    override val headers: TableHeader<T> = this@readWith.headers.map {
        SimpleColumnHeader(it.name, valueType.kType, it.meta)
    }

    override fun rowFlow(): Flow<TimeSeriesValues<T>> = this@readWith.rowFlow().map { row ->
        ValueWithTime(
            row.value.mapValues { entry -> reader.read(entry.value) },
            row.time
        )
    }
}


/**
 * Convert [TimeSeriesRows] of [Meta] to [TimeSeriesRows] of [Value] by extracting root value of [Meta]
 */
public fun TimeSeriesRows<Meta>.values(): TimeSeriesRows<Value> = readWith(MetaConverter.value, safeTypeOf<Value>())