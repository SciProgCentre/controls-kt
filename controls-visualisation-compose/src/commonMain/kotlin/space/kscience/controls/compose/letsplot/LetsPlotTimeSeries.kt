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

@OptIn(FlowPreview::class)
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
                if (now - current.time > interval*3) {
                    send(ValueWithTime(current.value, now))
                } else if (now - current.time < interval) {
                    send(current)
                }
            }
            delay(interval)
        }
    }

}


internal fun <T> Flow<ValueWithTime<T>>.collectAndTrim(
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    clock: Clock = Global.clock,
): Flow<List<ValueWithTime<T>>> {
    require(maxPoints > 2)
    require(minPoints > 0)
    require(maxPoints > minPoints)
    val points = mutableListOf<ValueWithTime<T>>()
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

@Stable
public class TimeSeriesPlotState {
    private val _data = mutableStateMapOf<String, List<ValueWithTime<Double>>>()
    public val data: Map<String, List<ValueWithTime<Double>>> get() = _data

    public fun updateSeries(name: String, points: List<ValueWithTime<Double>>) {
        _data[name] = points
    }

    public fun removeSeries(name: String) {
        _data.remove(name)
    }
}

public class TimeSeriesPlotBuilder(public val state: TimeSeriesPlotState)

@Composable
public fun TimeSeriesPlot(
    modifier: Modifier = Modifier.fillMaxSize(),
    xAxisTitle: String? = "Time",
    yAxisTitle: String? = "Value",
    drawInterval: Duration = defaultSampling,
    content: @Composable TimeSeriesPlotBuilder.() -> Unit
) {
    val state = remember { TimeSeriesPlotState() }
    val builder = remember { TimeSeriesPlotBuilder(state) }
    var plotData by remember { mutableStateOf<Map<String, List<*>>>(emptyMap()) }

    // launch all time series updates in this scope as effects
    content(builder)

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
    } + scaleXTime(name = xAxisTitle) + org.jetbrains.letsPlot.themes.theme().legendPositionBottom().legendDirectionVertical()

    if (yAxisTitle != null) {
        figure += labs(y = yAxisTitle)
    }

    PlotPanel(figure, modifier = modifier) {
        LoggerFactory.getLogger("LetsPlotTimeSeries").info(it.toString())
    }
}

@Composable
public fun TimeSeriesPlotBuilder.PlotDeviceProperty(
    device: Device,
    propertyName: String,
    seriesName: String = propertyName,
    extractValue: Meta.() -> Double = { double ?: Double.NaN },
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
) {
    LaunchedEffect(device, propertyName, maxAge, maxPoints, minPoints, sampling) {
        val clockManager = device.context.plugins.get<ClockManager>() ?: device.context.request(ClockManager)

        coroutineContext[Job]?.invokeOnCompletion {
            state.removeSeries(seriesName)
        }

        device.propertyMessageFlow(propertyName)
            .map { it.value.extractValue() }
            .repeatOrSample(clockManager, sampling)
            .collectAndTrim(maxAge, maxPoints, minPoints, device.clock)
            .onEach { state.updateSeries(seriesName, it) }
            .launchIn(this)
    }
}

@Composable
public fun TimeSeriesPlotBuilder.PlotDeviceProperty(
    device: Device,
    property: DevicePropertySpec<out Number>,
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
): Unit = PlotDeviceProperty(
    device = device,
    propertyName = property.name,
    extractValue = { property.converter.readOrNull(this)?.toDouble() ?: Double.NaN },
    maxAge = maxAge,
    maxPoints = maxPoints,
    minPoints = minPoints,
    sampling = sampling,
)

@Composable
public fun TimeSeriesPlotBuilder.PlotNumberState(
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
            state.removeSeries(seriesName)
        }


        valueState.subscribe()
            .map { it.toDouble() }
            .repeatOrSample(clockManager, sampling)
            .collectAndTrim(maxAge, maxPoints, minPoints, clock)
            .onEach { state.updateSeries(seriesName, it) }
            .launchIn(this)
    }
}

@Composable
public fun TimeSeriesPlotBuilder.PlotNumericState(
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

@OptIn(FlowPreview::class)
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
public fun TimeSeriesPlotBuilder.PlotAveragedDeviceProperty(
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
            state.removeSeries(seriesName)
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
            .onEach { state.updateSeries(seriesName, it) }
            .launchIn(this)
    }
}
