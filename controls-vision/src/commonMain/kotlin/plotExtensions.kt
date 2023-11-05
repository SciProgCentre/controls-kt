package space.kscience.controls.vision

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import space.kscience.controls.api.Device
import space.kscience.controls.api.propertyMessageFlow
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.manager.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.*
import space.kscience.plotly.Plot
import space.kscience.plotly.bar
import space.kscience.plotly.models.Bar
import space.kscience.plotly.models.Scatter
import space.kscience.plotly.models.TraceValues
import space.kscience.plotly.scatter

private var TraceValues.values: List<Value>
    get() = value?.list ?: emptyList()
    set(newValues) {
        value = ListValue(newValues)
    }

/**
 * Add a trace that shows a [Device] property change over time. Show only latest [pointsNumber] .
 * @return a [Job] that handles the listener
 */
public fun Plot.plotDeviceProperty(
    device: Device,
    propertyName: String,
    extractValue: Meta.() -> Value = { value ?: Null },
    pointsNumber: Int = 400,
    coroutineScope: CoroutineScope = device.context,
    configuration: Scatter.() -> Unit = {},
): Job = scatter(configuration).run {
    val clock = device.context.clock
    device.propertyMessageFlow(propertyName).onEach { message ->
        x.strings = (x.strings + (message.time ?: clock.now()).toString()).takeLast(pointsNumber)
        y.values = (y.values + message.value.extractValue()).takeLast(pointsNumber)
    }.launchIn(coroutineScope)
}


public fun Plot.plotNumberState(
    context: Context,
    state: DeviceState<out Number>,
    pointsNumber: Int = 400,
    configuration: Scatter.() -> Unit = {},
): Job = scatter(configuration).run {
    val clock = context.clock
    state.valueFlow.onEach {
        x.strings = (x.strings + clock.now().toString()).takeLast(pointsNumber)
        y.numbers = (y.numbers + it).takeLast(pointsNumber)
    }.launchIn(context)
}

public fun Plot.plotBooleanState(
    context: Context,
    state: DeviceState<Boolean>,
    pointsNumber: Int = 400,
    configuration: Bar.() -> Unit = {},
): Job = bar(configuration).run {
    val clock = context.clock
    state.valueFlow.onEach {
        x.strings = (x.strings + clock.now().toString()).takeLast(pointsNumber)
        y.values = (y.values + it.asValue()).takeLast(pointsNumber)
    }.launchIn(context)
}