package space.kscience.controls.vision

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
import kotlin.time.Duration.Companion.hours

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

/**
 * Add a trace that shows a [Device] property change over time. Show only latest [maxPoints] .
 * @return a [Job] that handles the listener
 */
public fun Plot.plotDeviceProperty(
    device: Device,
    propertyName: String,
    extractValue: Meta.() -> Value = { value ?: Null },
    maxAge: Duration = 1.hours,
    maxPoints: Int = 800,
    minPoints: Int = 400,
    coroutineScope: CoroutineScope = device.context,
    configuration: Scatter.() -> Unit = {},
): Job = scatter(configuration).run {
    val clock = device.context.clock
    val data = TimeData()
    device.propertyMessageFlow(propertyName).transform {
        data.append(it.time ?: clock.now(), it.value.extractValue())
        data.trim(maxAge, maxPoints, minPoints)
        emit(data)
    }.onEach {
        it.fillPlot(x, y)
    }.launchIn(coroutineScope)
}

private fun <T> Trace.updateFromState(
    context: Context,
    state: DeviceState<T>,
    extractValue: T.() -> Value = { state.converter.objectToMeta(this).value ?: space.kscience.dataforge.meta.Null },
    maxAge: Duration = 1.hours,
    maxPoints: Int = 800,
    minPoints: Int = 400,
): Job{
    val clock = context.clock
    val data = TimeData()
    return state.valueFlow.transform<T, TimeData> {
        data.append(clock.now(), it.extractValue())
        data.trim(maxAge, maxPoints, minPoints)
    }.onEach {
        it.fillPlot(x, y)
    }.launchIn(context)
}

public fun <T> Plot.plotDeviceState(
    context: Context,
    state: DeviceState<T>,
    extractValue: T.() -> Value = { state.converter.objectToMeta(this).value ?: Null },
    maxAge: Duration = 1.hours,
    maxPoints: Int = 800,
    minPoints: Int = 400,
    configuration: Scatter.() -> Unit = {},
): Job = scatter(configuration).run {
    updateFromState(context, state, extractValue, maxAge, maxPoints, minPoints)
}


public fun Plot.plotNumberState(
    context: Context,
    state: DeviceState<out Number>,
    maxAge: Duration = 1.hours,
    maxPoints: Int = 800,
    minPoints: Int = 400,
    configuration: Scatter.() -> Unit = {},
): Job = scatter(configuration).run {
    updateFromState(context, state, { asValue() }, maxAge, maxPoints, minPoints)
}


public fun Plot.plotBooleanState(
    context: Context,
    state: DeviceState<Boolean>,
    maxAge: Duration = 1.hours,
    maxPoints: Int = 800,
    minPoints: Int = 400,
    configuration: Bar.() -> Unit = {},
): Job =  bar(configuration).run {
    updateFromState(context, state, { asValue() }, maxAge, maxPoints, minPoints)
}