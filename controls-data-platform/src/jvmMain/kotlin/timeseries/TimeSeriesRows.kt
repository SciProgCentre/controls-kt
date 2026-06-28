package space.kscience.controls.dataplatform.timeseries

import kotlinx.coroutines.flow.*
import space.kscience.attributes.SafeType
import space.kscience.attributes.safeTypeOf
import space.kscience.controls.dataplatform.TagTable.Companion.timeColumnHeader
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MetaReader
import space.kscience.dataforge.meta.Value
import space.kscience.tables.*
import kotlin.time.Instant

public typealias TimeSeriesValues<T> = ValueWithTime<Map<String, T>>

public fun Meta(time: Instant): Meta = Meta(time.toString())

internal fun TimeSeriesValues<Meta>.toRow(): Row<Meta> = MapRow(value + (timeColumnHeader.name to Meta(time)))


/**
 * A source of time series rows
 */
public interface TimeSeriesRows<T> {
    /**
     * An ordered list of headers that *must* be present.
     */
    public val headers: TableHeader<T>

    /**
     * A dynamic flow of rows
     */
    public fun subscribe(): Flow<TimeSeriesValues<T>>
}

public class TimeSeriesRowsFlow<T>(
    override val headers: TableHeader<T>,
    private val flow: SharedFlow<TimeSeriesValues<T>>
) : TimeSeriesRows<T> {
    override fun subscribe(): Flow<TimeSeriesValues<T>> = flow
}

/**
 * Collect [rowNum] rows from the source and represent them as a table
 */
public suspend fun TimeSeriesRows<Meta>.collectTable(rowNum: Int): RowTable<Meta> {
    val rows = subscribe().map {
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

    override fun subscribe(): Flow<TimeSeriesValues<T>> = this@readWith.subscribe().map { row ->
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