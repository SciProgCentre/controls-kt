package center.sciprog.controls.demo.thermo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import org.jetbrains.compose.web.dom.ElementScope
import org.w3c.dom.Element
import space.kscience.dataforge.meta.MetaRepr
import space.kscience.dataforge.meta.MetaSerializer
import space.kscience.plotly.Plot
import space.kscience.plotly.Plotly
import space.kscience.plotly.PlotlyConfig
import space.kscience.plotly.PlotlyJs
import space.kscience.visionforge.VisionGroupCompositionChangedEvent
import space.kscience.visionforge.VisionPropertyChangedEvent


@OptIn(ExperimentalSerializationApi::class)
private fun MetaRepr.toDynamic(): dynamic = Json.encodeToDynamic(MetaSerializer, toMeta())

private fun List<MetaRepr>.toDynamic(): Array<dynamic> = map { it.toDynamic() }.toTypedArray()

@Composable
public fun ElementScope<Element>.Plot(plotlyConfig: PlotlyConfig = PlotlyConfig.empty(), plot: Plot) {
    val scope = rememberCoroutineScope()

    DisposableEffect(plot) {

        PlotlyJs.react(
            graphDiv = scopeElement,
            data = plot.data.toDynamic(),
            layout = plot.layout.toDynamic(),
            config = plotlyConfig.toDynamic()
        )

        //start updates
        val listenJob = scope.launch {
            plot.data.forEachIndexed { index, trace ->
                trace.eventFlow.filterIsInstance<VisionPropertyChangedEvent>().onEach { event ->
                    val traceData = trace.toDynamic()

                    Plotly.coordinateNames.forEach { coordinate ->
                        val data = traceData[coordinate]
                        if (traceData[coordinate] != null) {
                            traceData[coordinate] = arrayOf(data)
                        }
                    }

                    PlotlyJs.restyle(scopeElement, traceData, arrayOf(index))
                }.launchIn(this)
            }

            plot.eventFlow.onEach { event ->
                when (event) {
                    is VisionGroupCompositionChangedEvent -> PlotlyJs.restyle(scopeElement, plot.data.toDynamic())
                    is VisionPropertyChangedEvent -> PlotlyJs.relayout(scopeElement, plot.layout.toDynamic())
                    else -> {
                        //ignore
                    }
                }
            }.launchIn(this)
        }

        onDispose {
            listenJob.cancel()
        }
    }
}

@Composable
public fun ElementScope<Element>.Plot(plotlyConfig: PlotlyConfig = PlotlyConfig.empty(), plot: Plot.() -> Unit) {
    Plot(plotlyConfig, Plotly.plot(plot))
}