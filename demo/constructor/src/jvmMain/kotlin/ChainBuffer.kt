@file:OptIn(ExperimentalKoalaPlotApi::class, ExperimentalSplitPaneApi::class)

package space.kscience.controls.demo.constructor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.legend.FlowLegend
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.toString
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberDoubleLinearAxisModel
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.compose.PlotNumericState
import space.kscience.controls.compose.TimeAxisModel
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.models.continuous.*
import space.kscience.controls.constructor.units.CubicMeters
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import java.awt.Dimension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant

private class ChainBufferModel(
    context: Context
) : ContinuousFlowModel(context) {
    val production = MutableDeviceState(Numeric<Kilograms>(1.0))
    val producer = producer(Kilograms, production).apply {
        debugState("Producer production", production)
    }

    val buffer1 = buffer(Kilograms, Numeric(10.0)).apply {
        connectProducer(producer)
    }

    val transformer = linearTransformer(Kilograms, CubicMeters, Numeric(1.0)).apply {
        connectProducer(buffer1)
    }

    val buffer2 = buffer(CubicMeters, Numeric(10.0)).apply {
        connectProducer(transformer.limited(this, Numeric(2.0)))
        debugState("Buffer 2", content)
    }

    val consumation = MutableDeviceState(Numeric<CubicMeters>(2.0))

    val consumer = consumer(CubicMeters, consumation).apply {
        connectProducer(buffer2)
        debugState("Consumer consumption", consumation)
    }
}

fun main() {
    val context = Context {
        plugin(DeviceManager)
        plugin(ClockManager)
    }

    val model = ChainBufferModel(context)

    val maxAge = 60.seconds


    application {

        Window(title = "Chemical factory", onCloseRequest = ::exitApplication) {
            window.minimumSize = Dimension(400, 400)
            MaterialTheme {
                HorizontalSplitPane {
                    first(200.dp) {
                        Column(modifier = Modifier.background(color = Color.LightGray).fillMaxHeight().fillMaxWidth()) {
                            Row {
                                Text("Enable producer", modifier = Modifier.align(Alignment.CenterVertically))

                                val checked by model.production.subscribe().map { it.value > 0.0 }.collectAsState(true)
                                Checkbox(checked, onCheckedChange = {
                                    if (it) {
                                        model.production.value = Numeric(1.0)
                                    } else {
                                        model.production.value = Numeric(0.0)
                                    }
                                })
                            }
                            Row {
                                Text("Enable consumer", modifier = Modifier.align(Alignment.CenterVertically))

                                val checked by model.consumation.subscribe().map { it.value > 0.0 }.collectAsState(true)
                                Checkbox(checked, onCheckedChange = {
                                    if (it) {
                                        model.consumation.value = Numeric(2.0)
                                    } else {
                                        model.consumation.value = Numeric(0.0)
                                    }
                                })
                            }
                        }

                    }
                    second(400.dp) {
                        ChartLayout {
                            XYGraph<Instant, Double>(
                                xAxisModel = remember { TimeAxisModel.recent(maxAge, context.clock) },
                                yAxisModel = rememberDoubleLinearAxisModel(0.0..12.0),
                                xAxisTitle = { Text("Time in seconds relative to current") },
                                xAxisLabels = { it: Instant ->
                                    Text(
                                        (context.clock.now() - it).toDouble(
                                            DurationUnit.SECONDS
                                        ).toString(2)
                                    )
                                },
                                yAxisLabels = { it: Double -> Text(it.toString(2)) }
                            ) {
                                PlotNumericState(
                                    context = context,
                                    state = model.consumer.consumation,
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Blue))
                                )
                                PlotNumericState(
                                    context = context,
                                    state = model.buffer1.content,
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Magenta))
                                )
                                PlotNumericState(
                                    context = context,
                                    state = model.transformer.production,
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Red))
                                )
                                PlotNumericState(
                                    context = context,
                                    state = model.buffer2.content,
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Black))
                                )
                                PlotNumericState(
                                    context = context,
                                    state = model.producer.production,
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Green))
                                )

                            }
                            Surface {
                                FlowLegend(5, label = {
                                    when (it) {
                                        0 -> {
                                            Text("Total product", color = Color.Blue)
                                        }

                                        1 -> {
                                            Text("Buffer 1", color = Color.Magenta)
                                        }

                                        2 -> {
                                            Text("Transformer production", color = Color.Red)
                                        }

                                        3 -> {
                                            Text("Buffer 2", color = Color.Black)
                                        }

                                        4 -> {
                                            Text("Producer production", color = Color.Green)
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}