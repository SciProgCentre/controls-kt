@file:OptIn(FlowPreview::class)

package space.kscience.controls.vision

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.kscience.controls.api.Device
import space.kscience.controls.api.propertyMessageFlow
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.manager.clock
import space.kscience.controls.misc.ValueWithTime
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.name
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.*
import space.kscience.plotly.Plot
import space.kscience.plotly.bar
import space.kscience.plotly.models.Bar
import space.kscience.plotly.models.Scatter
import space.kscience.plotly.models.Trace
import space.kscience.plotly.models.TraceValues
import space.kscience.plotly.scatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private var TraceValues.values: List<Value>
    get() = value?.list ?: emptyList()
    set(newValues) {
        value = ListValue(newValues)
    }


private var TraceValues.times: List<Instant>
    get() = value?.list?.map { Instant.parse(it.string) } ?: emptyList()
    set(newValues) {
        value = ListValue(newValues.map { it.toString().asValue() })
    }


private class TimeData(private var points: MutableList<ValueWithTime<Value>> = mutableListOf()) {
    private val mutex = Mutex()

    suspend fun append(time: Instant, value: Value) = mutex.withLock {
        points.add(ValueWithTime(value, time))
    }

    suspend fun trim(maxAge: Duration, maxPoints: Int = 800, minPoints: Int = 400) {
        require(maxPoints > 2)
        require(minPoints > 0)
        require(maxPoints > minPoints)
        val now = Clock.System.now()
        // filter old points
        points.removeAll { now - it.time > maxAge }

        if (points.size > maxPoints) {
            val durationBetweenPoints = maxAge / minPoints
            val markedForRemoval = buildList<ValueWithTime<Value>> {
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
    }

    suspend fun fillPlot(x: TraceValues, y: TraceValues) = mutex.withLock {
        x.strings = points.map { it.time.toString() }
        y.values = points.map { it.value }
    }
}

private val defaultMaxAge get() = 10.minutes
private val defaultMaxPoints get() = 800
private val defaultMinPoints get() = 400
private val defaultSampling get() = 1.seconds

/**
 * Add a trace that shows a [Device] property change over time. Show only latest [maxPoints] .
 * @return a [Job] that handles the listener
 */
public fun Plot.plotDeviceProperty(
    device: Device,
    propertyName: String,
    extractValue: Meta.() -> Value = { value ?: Null },
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
    coroutineScope: CoroutineScope = device.context,
    configuration: Scatter.() -> Unit = {},
): Job = scatter(configuration).run {
    val data = TimeData()
    device.propertyMessageFlow(propertyName).sample(sampling).transform {
        data.append(it.time, it.value.extractValue())
        data.trim(maxAge, maxPoints, minPoints)
        emit(data)
    }.onEach {
        it.fillPlot(x, y)
    }.launchIn(coroutineScope)
}

public fun Plot.plotDeviceProperty(
    device: Device,
    property: DevicePropertySpec<*, Number>,
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
    coroutineScope: CoroutineScope = device.context,
    configuration: Scatter.() -> Unit = {},
): Job = plotDeviceProperty(
    device, property.name, { value ?: Null }, maxAge, maxPoints, minPoints, sampling, coroutineScope, configuration
)

private fun <T> Trace.updateFromState(
    context: Context,
    state: DeviceState<T>,
    extractValue: T.() -> Value,
    maxAge: Duration,
    maxPoints: Int,
    minPoints: Int,
    sampling: Duration,
): Job {
    val clock = context.clock
    val data = TimeData()
    return state.valueFlow.sample(sampling).transform<T, TimeData> {
        data.append(clock.now(), it.extractValue())
        data.trim(maxAge, maxPoints, minPoints)
    }.onEach {
        it.fillPlot(x, y)
    }.launchIn(context)
}

public fun <T> Plot.plotDeviceState(
    context: Context,
    state: DeviceState<T>,
    extractValue: T.() -> Value = { state.converter.convert(this).value ?: Null },
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
    configuration: Scatter.() -> Unit = {},
): Job = scatter(configuration).run {
    updateFromState(context, state, extractValue, maxAge, maxPoints, minPoints, sampling)
}


public fun Plot.plotNumberState(
    context: Context,
    state: DeviceState<out Number>,
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
    configuration: Scatter.() -> Unit = {},
): Job = scatter(configuration).run {
    updateFromState(context, state, { asValue() }, maxAge, maxPoints, minPoints, sampling)
}


public fun Plot.plotBooleanState(
    context: Context,
    state: DeviceState<Boolean>,
    maxAge: Duration = defaultMaxAge,
    maxPoints: Int = defaultMaxPoints,
    minPoints: Int = defaultMinPoints,
    sampling: Duration = defaultSampling,
    configuration: Bar.() -> Unit = {},
): Job = bar(configuration).run {
    updateFromState(context, state, { asValue() }, maxAge, maxPoints, minPoints, sampling)
}