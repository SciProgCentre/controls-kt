package space.kscience.controls.dataplatform.timeseries

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.time.ValueWithTime
import space.kscience.kmath.operations.BufferFieldOps
import space.kscience.kmath.operations.Float64Field
import space.kscience.kmath.operations.algebra
import space.kscience.kmath.operations.bufferAlgebra
import space.kscience.kmath.series.MonotonicSeriesAlgebra
import space.kscience.kmath.series.SeriesAlgebra
import space.kscience.kmath.structures.Float64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.times

internal fun SeriesAlgebra.Companion.labeledByTime(
    zero: Instant,
    step: Duration
): MonotonicSeriesAlgebra<Float64, Float64Field, BufferFieldOps<Float64, Float64Field>, Instant> =
    MonotonicSeriesAlgebra(
        bufferAlgebra = Double.algebra.bufferAlgebra,
        offsetToLabel = { zero + step * it },
        labelToOffset = { (it - zero) / step }
    )


//TODO consider using other types

/**
 * A class for collecting and managing time series data from multiple sources within a specified time range.
 *
 * @constructor Initializes the collector with the given configuration and starts monitoring the sources.
 * @param scope The scope for launching coroutines that manage the collection tasks.
 * @param zero The starting point of the time series in terms of a specific instant of time.
 * @param step The time interval between consecutive data points in the time series.
 * @param size The maximum number of data points to retain for each time series.
 * @param sources A map of source identifiers to their corresponding time series data sources.
 * @param clock The clock used to measure time. Defaults to the system clock.
 *
 * @property series A map containing the rolling series for each source, indexed by their identifiers.
 * The series are updated dynamically as new data is collected from the sources.
 */
public class TimeSeriesCollector(
    public val scope: CoroutineScope,
    public val zero: Instant,
    public val step: Duration,
    public val size: Int,
    public val sources: Map<String, TimeSeriesSource<Float64>>,
    public val clock: Clock = Clock.System
) {
    private val algebra = SeriesAlgebra.labeledByTime(zero, step)

    public val series: Map<String, RollingSeries<Float64>> =
        sources.mapValues { RollingSeries(size, algebra.elementAlgebra) }

    private val updateJob = scope.launch {
        sources.forEach { (name, source) ->
            var lastStep = 0
            val collectedValues = ArrayList<ValueWithTime<Float64>>()
            val mutex = Mutex()
            source.subscribe().onEach { (value, time) ->
                //ignore late events from the previous series step
                if (time < zero + lastStep * step) return@onEach
                //wait for events in the next interval
                if (time >= (zero + (lastStep + 1) * step)) {
                    mutex.withLock {
                        if (collectedValues.isNotEmpty()) {
                            val averagedValue = collectedValues.map { it.value }.average()
                            collectedValues.clear()
                            series[name]!!.push(averagedValue)
                        } else {
                            //keep the previous value or move start
                            series[name]!!.skip()
                        }
                    }
                }
                // update collected values
                mutex.withLock {
                    collectedValues += ValueWithTime(value, time)
                }
            }.launchIn(scope)
        }
    }

//    fun asTable(): Table
}