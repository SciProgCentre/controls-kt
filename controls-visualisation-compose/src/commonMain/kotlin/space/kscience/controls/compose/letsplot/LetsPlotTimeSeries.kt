/* LLM generated code: Let's Plot integration for controls-kt */
package space.kscience.controls.compose.letsplot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.letsPlot.compose.PlotPanel
import org.jetbrains.letsPlot.geom.geomLine
import org.jetbrains.letsPlot.label.labs
import org.jetbrains.letsPlot.letsPlot
import org.jetbrains.letsPlot.scale.scaleXTime
import org.slf4j.LoggerFactory
import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.propertyMessageFlow
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.values
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.name
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.ValueHistory
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val defaultMaxAge get() = 10.minutes
private val defaultMaxPoints get() = 800
private val defaultMinPoints get() = 400
private val defaultSampling get() = 1.seconds

/**
 * Repeats or samples the incoming values from the flow based on the provided clock and interval.
 * If no new values are received within a certain duration, it generates a sampled value.
 *
 * @param clockManager Manages clock-related functionality, providing the clock and dispatcher for time-based operations.
 * @param interval The duration of the interval at which the flow is sampled or values are repeated.
 * @return A flow of `ValueWithTime` objects containing the incoming values and their associated timestamps.
 */
private fun <T> Flow<T>.repeatOrSample(
    clockManager: ClockManager,
    interval: Duration
): Flow<ValueWithTime<T>> = channelFlow {
    withContext(clockManager.simulationDispatcher) {
        val clock = clockManager.clock

        var currentValue: ValueWithTime<T>? = null

        launch {
            collect {
                currentValue = ValueWithTime(it, clock.now())
            }
        }

        while (isActive) {
            currentValue?.let { current ->
                val now = clock.now()
                if (now - current.time > interval * 3) {
                    send(ValueWithTime(current.value, now))
                } else if (now - current.time < interval) {
                    send(current)
                }
            }
            delay(interval)
        }
    }

}

/**
 * Collects and processes a flow of `ValueWithTime` objects, trimming outdated values and limiting
 * the number of points while ensuring a minimum required count.
 *
 * @param maxAge The maximum age duration for which data points remain valid. Points older than this duration are removed. Defaults to `defaultMaxAge`.
 * @param maxPoints The maximum number of points allowed in the collected list. Points exceeding this limit are removed while maintaining the latest points. Defaults to `defaultMax
 * Points`.
 * @param minPoints The minimum number of points that must remain in the list after trimming. Defaults to `defaultMinPoints`.
 * @param clock The clock instance used to derive the current time for processing. Defaults to `Global.clock`.
 * @return A flow of lists containing the trimmed and valid `ValueWithTime` points.
 */
internal fun <T> Flow<ValueWithTime<T>>.collectAndTrim(
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    clock: Clock = Global.clock,
    startingPoints: List<ValueWithTime<T>> = emptyList()
): Flow<List<ValueWithTime<T>>> {
    require(maxPoints > 2)
    require(minPoints > 0)
    require(maxPoints > minPoints)
    val points = startingPoints.toMutableList()
    return transform { newPoint ->
        points.add(newPoint)
        val now = clock.now()
        // filter old points
        points.removeAll { now - it.time > maxAge }

        if (points.size > maxPoints) {
            val durationBetweenPoints = maxAge / minPoints
            val markedForRemoval = buildList {
                var lastTime: Instant? = null
                points.forEach { point ->
                    if (lastTime?.let { point.time - it < durationBetweenPoints } == true) {
                        add(point)
                    } else {
                        lastTime = point.time
                    }
                }
            }

            points.removeAll(markedForRemoval)
        }
        //return a protective copy
        emit(ArrayList(points))
    }
}

/**
 * Manages and maintains the state of time series data for visualization in a plot.
 *
 * This state class allows you to store, update, and remove time series data by associating
 * a series name (String) with a list of time-stamped values. Each value within a time series
 * is represented as a `ValueWithTime<Double>` object.
 *
 * The state is utilized by classes and functions that implement time series plotting,
 * ensuring synchronization between stored data and its visual representation.
 *
 * @property data A read-only map where keys are series names and values are lists of
 * time-stamped values representing the time series data.
 */
@Stable
public class TimeSeriesPlotState {
    public val data: Map<String, List<ValueWithTime<Double>>>
        field = mutableStateMapOf<String, List<ValueWithTime<Double>>>()

    public fun updateSeries(name: String, points: List<ValueWithTime<Double>>) {
        data[name] = points
    }

    public fun removeSeries(name: String) {
        data.remove(name)
    }
}


@Composable
public fun TimeSeriesPlot(
    modifier: Modifier = Modifier.fillMaxSize(),
    xAxisTitle: String? = "Time",
    yAxisTitle: String? = "Value",
    drawInterval: Duration = defaultSampling,
    content: @Composable TimeSeriesPlotState.() -> Unit
) {
    val state = remember { TimeSeriesPlotState() }
    var plotData by remember { mutableStateOf<Map<String, List<*>>>(emptyMap()) }

    // launch all time series updates in this scope as effects
    content(state)

    LaunchedEffect(Unit) {
        while (isActive) {
            val time = mutableListOf<Long>()
            val value = mutableListOf<Double>()
            val series = mutableListOf<String>()

            state.data.forEach { (name, points) ->
                points.forEach { p ->
                    time.add(p.time.toEpochMilliseconds())
                    value.add(p.value)
                    series.add(name)
                }
            }

            plotData = mapOf(
                "time" to time,
                "value" to value,
                "series" to series
            )
            delay(drawInterval)
        }
    }

    if (plotData["time"]?.isEmpty() != false) {
        return
    }

    var figure = letsPlot(plotData) + geomLine {
        x = "time"
        y = "value"
        color = "series"
    } + scaleXTime(name = xAxisTitle) + org.jetbrains.letsPlot.themes.theme().legendPositionBottom()
        .legendDirectionVertical()

    if (yAxisTitle != null) {
        figure += labs(y = yAxisTitle)
    }

    PlotPanel(figure, modifier = modifier) {
        LoggerFactory.getLogger("LetsPlotTimeSeries").info(it.toString())
    }
}

@Composable
public fun TimeSeriesPlotState.PlotDeviceProperty(
    device: Device,
    propertyName: String,
    seriesName: String = propertyName,
    extractValue: Meta.() -> Double? = { double },
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
    history: ValueHistory<Meta>? = null
) {
    LaunchedEffect(device, propertyName, maxAge, maxPoints, minPoints, sampling) {
        val clockManager = device.context.plugins.get<ClockManager>() ?: device.context.request(ClockManager)

        coroutineContext[Job]?.invokeOnCompletion {
            removeSeries(seriesName)
        }

        // if history not null, use it to pre-fill old values
        val startingPoints = history
            ?.flow(clockManager.clock.now().minus(maxAge))
            ?.mapNotNull { (value, time) ->
                extractValue(value)?.let { ValueWithTime(it, time) }
            }?.toList()
            ?: emptyList()

        device.propertyMessageFlow(propertyName)
            .mapNotNull { it.value.extractValue() }
            .repeatOrSample(clockManager, sampling)
            .collectAndTrim(maxAge, maxPoints, minPoints, device.clock, startingPoints)
            .onEach { updateSeries(seriesName, it) }
            .launchIn(this)
    }
}

@Composable
public fun TimeSeriesPlotState.PlotDeviceProperty(
    device: Device,
    property: DevicePropertySpec<out Number>,
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
    history: ValueHistory<Meta>? = null
): Unit = PlotDeviceProperty(
    device = device,
    propertyName = property.name,
    extractValue = { property.converter.readOrNull(this)?.toDouble() ?: Double.NaN },
    maxAge = maxAge,
    maxPoints = maxPoints,
    minPoints = minPoints,
    sampling = sampling,
    history = history
)

@Composable
public fun TimeSeriesPlotState.PlotNumberState(
    context: Context,
    valueState: ValueState<Number>,
    seriesName: String = valueState.toString(),
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
): Unit {
    LaunchedEffect(context, valueState, maxAge, maxPoints, minPoints, sampling) {
        val clockManager = context.plugins.get<ClockManager>() ?: context.request(ClockManager)
        val clock = context.clock

        coroutineContext[Job]?.invokeOnCompletion {
            removeSeries(seriesName)
        }


        valueState.subscribe()
            .map { it.toDouble() }
            .repeatOrSample(clockManager, sampling)
            .collectAndTrim(maxAge, maxPoints, minPoints, clock)
            .onEach { updateSeries(seriesName, it) }
            .launchIn(this)
    }
}

@Composable
public fun TimeSeriesPlotState.PlotNumericState(
    context: Context,
    state: ValueState<Amount<*>>,
    name: String = state.toString(),
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
): Unit {
    PlotNumberState(context, state.values(), name, maxAge, maxPoints, minPoints, sampling)
}

private fun List<Instant>.averageTime(): Instant {
    if (isEmpty()) return Instant.DISTANT_PAST
    val min = min()
    val max = max()
    val duration = max - min
    return min + duration / 2
}

private fun <T> Flow<T>.chunkedByPeriod(duration: Duration): Flow<List<T>> {
    val collector: ArrayDeque<T> = ArrayDeque<T>()
    return channelFlow {
        launch {
            while (isActive) {
                delay(duration)
                send(ArrayList(collector))
                collector.clear()
            }
        }
        this@chunkedByPeriod.collect {
            collector.add(it)
        }
    }
}

@Composable
public fun TimeSeriesPlotState.PlotAveragedDeviceProperty(
    device: Device,
    propertyName: String,
    startValue: Double = 0.0,
    seriesName: String = propertyName,
    extractValue: Meta.() -> Double = { value?.double ?: startValue },
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    averagingInterval: Duration = defaultSampling,
) {
    LaunchedEffect(device, propertyName, startValue, maxAge, maxPoints, minPoints, averagingInterval) {
        val clock: Clock = device.clock
        var lastValue = startValue

        coroutineContext[Job]?.invokeOnCompletion {
            removeSeries(seriesName)
        }

        device.propertyMessageFlow(propertyName)
            .chunkedByPeriod(averagingInterval)
            .transform<List<PropertyChangedMessage>, ValueWithTime<Double>> { eventList ->
                if (eventList.isEmpty()) {
                    ValueWithTime(lastValue, clock.now())
                } else {
                    val time = eventList.map { it.time }.averageTime()
                    val value = eventList.map { extractValue(it.value) }.average()
                    ValueWithTime(value, time).also {
                        lastValue = value
                    }
                }
            }.collectAndTrim(maxAge, maxPoints, minPoints, clock)
            .onEach { updateSeries(seriesName, it) }
            .launchIn(this)
    }
}
