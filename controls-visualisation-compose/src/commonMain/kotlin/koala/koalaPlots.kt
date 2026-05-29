@file:OptIn(FlowPreview::class)

package space.kscience.controls.compose.koala

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import io.github.koalaplot.core.line.LinePlot
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.XYGraphScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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

private fun <T> Flow<T>.repeatOrSample(clockManager: ClockManager, interval: Duration): Flow<ValueWithTime<T>> = flow {
    val clock = clockManager.clock

    coroutineScope {

        var current: T? = null
        var flag: Boolean = false

        launch {
            collect {
                current = it
                flag = true
            }
        }

        while (isActive) {
            current?.let {
                if (!flag) {
                    emit(ValueWithTime(it, clock.now()))
                }
            }
            flag = false
            withContext(clockManager.simulationDispatcher) {
                delay(interval)
            }
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

private val defaultLineStyle: LineStyle = LineStyle(SolidColor(Color.Black))


@Composable
private fun <T> XYGraphScope<Instant, T>.PlotTimeSeries(
    data: List<ValueWithTime<T>>,
    lineStyle: LineStyle = defaultLineStyle,
) {
    LinePlot(
        data = data.map { DefaultPoint(it.time, it.value) },
        lineStyle = lineStyle
    )
}


/**
 * Add a trace that shows a [Device] property change over time. Show only latest [maxPoints] .
 * @return a [Job] that handles the listener
 */
@Composable
public fun XYGraphScope<Instant, Double>.PlotDeviceProperty(
    device: Device,
    propertyName: String,
    extractValue: Meta.() -> Double = { value?.double ?: Double.NaN },
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
    lineStyle: LineStyle = defaultLineStyle,
) {
    var points by remember { mutableStateOf<List<ValueWithTime<Double>>>(emptyList()) }
    val clockManager =
        remember(device) { device.context.plugins.get<ClockManager>() ?: device.context.request(ClockManager) }

    LaunchedEffect(device, propertyName, maxAge, maxPoints, minPoints, sampling) {
        device.propertyMessageFlow(propertyName)
            .map { it.value.extractValue() }
            .repeatOrSample(clockManager, sampling)
            .collectAndTrim(maxAge, maxPoints, minPoints, device.clock)
            .onEach { points = it }
            .launchIn(this)
    }


    PlotTimeSeries(points, lineStyle)
}

@Composable
public fun XYGraphScope<Instant, Double>.PlotDeviceProperty(
    device: Device,
    property: DevicePropertySpec<out Number>,
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
    lineStyle: LineStyle = LineStyle(SolidColor(Color.Black)),
): Unit = PlotDeviceProperty(
    device = device,
    propertyName = property.name,
    extractValue = { property.converter.readOrNull(this)?.toDouble() ?: Double.NaN },
    maxAge = maxAge,
    maxPoints = maxPoints,
    minPoints = minPoints,
    sampling = sampling,
    lineStyle = lineStyle
)

@Composable
public fun XYGraphScope<Instant, Double>.PlotNumberState(
    context: Context,
    state: ValueState<Number>,
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
    lineStyle: LineStyle = defaultLineStyle,
): Unit {
    var points by remember { mutableStateOf<List<ValueWithTime<Double>>>(emptyList()) }
    val clockManager = remember(context) { context.plugins.get<ClockManager>() ?: context.request(ClockManager) }

    LaunchedEffect(context, state, maxAge, maxPoints, minPoints, sampling) {
        val clock = context.clock

        state.subscribe()
            .map { it.toDouble() }
            .repeatOrSample(clockManager, sampling)
            .collectAndTrim(maxAge, maxPoints, minPoints, clock)
            .onEach { points = it }
            .launchIn(this)
    }


    PlotTimeSeries(points, lineStyle)
}

@Composable
public fun XYGraphScope<Instant, Double>.PlotNumericState(
    context: Context,
    state: ValueState<Amount<*>>,
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
    lineStyle: LineStyle = defaultLineStyle,
): Unit {
    PlotNumberState(context, state.values(), maxAge, maxPoints, minPoints, sampling, lineStyle)
}


private fun List<Instant>.averageTime(): Instant {
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


/**
 * Average property value by [averagingInterval]. Return [startValue] on each sample interval if no events arrived.
 */
@Composable
public fun XYGraphScope<Instant, Double>.PlotAveragedDeviceProperty(
    device: Device,
    propertyName: String,
    startValue: Double = 0.0,
    extractValue: Meta.() -> Double = { value?.double ?: startValue },
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    averagingInterval: Duration = defaultSampling,
    lineStyle: LineStyle = defaultLineStyle,
) {

    var points by remember { mutableStateOf<List<ValueWithTime<Double>>>(emptyList()) }

    LaunchedEffect(device, propertyName, startValue, maxAge, maxPoints, minPoints, averagingInterval) {
        val clock: Clock = device.clock
        var lastValue = startValue
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
            .onEach { points = it }
            .launchIn(this)
    }

    PlotTimeSeries(points, lineStyle)
}