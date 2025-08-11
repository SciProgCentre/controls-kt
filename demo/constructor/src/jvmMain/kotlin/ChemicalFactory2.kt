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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.compose.PlotNumericState
import space.kscience.controls.compose.TimeAxisModel
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.models.continuous.*
import space.kscience.controls.constructor.units.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import java.awt.Dimension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant

internal fun StateContainer.debugState(name: String, state: DeviceState<Amount<*>>): Job =
    state.useValue(this) { value ->
        println("(${clock.now()}) $name: ${value.value}")
    }


private class ChemicalFactory2(
    context: Context
) : ContinuousFlowModel(context) {
    val aProduction = MutableDeviceState(Numeric<Kilograms>(4.0))
    val aProducer = producer(aProduction)

    val bProduction = MutableDeviceState(Numeric<Kilograms>(2.0))
    val bProducer = producer(bProduction)

    val joinAB = mix(kilograms, listOf("a", "b")).apply {
        connectProducer("a", aProducer)
        connectProducer("b", bProducer)
    }

    val cProduction = MutableDeviceState(Numeric<CubicMeters>(2.5)).apply {
//        onTimer(0.2.seconds) { _, _ ->
//            value = Numeric(3.0 + Random.nextDouble(-0.1, 0.1))
//        }
    }

    val cProducer = producer(cProduction)

    val cBuffer = buffer(cubicMeters, Numeric(10.0)).apply {
        connectProducer(cProducer)

        debugState("C buffer level", content)
        debugState("C buffer request", consumationCapacity)
        debugState("C buffer consumption", consumation)
        debugState("C buffer production", production)
    }

    val transformer = linearTransformer(cubicMeters, kilograms, Numeric(1.0)).apply {
        connectProducer(cBuffer)
    }

    val reactor = reaction(
        algebra = kilograms,
        formula = mapOf("ab" to Numeric(0.66), "c" to Numeric(0.33)),
    ).apply {
        connectProducer("ab", joinAB)
        connectProducer("c", transformer)
        debugState("Reactor consumation request AB", individualConsumationCapacity["ab"]!!)
        debugState("Reactor consumation requesst C", individualConsumationCapacity["c"]!!)
    }

    val consumation = MutableDeviceState(Numeric<Kilograms>(10.0))

    val consumer = consumer(consumation).apply {
        connectProducer(reactor)
        debugState("consumation", consumation)
    }

    companion object {
        val kilograms = NumericAmountAlgebra<Kilograms>()
        val cubicMeters = NumericAmountAlgebra<CubicMeters>()
    }
}

fun main() {
    val context = Context {
        plugin(DeviceManager)
        plugin(ClockManager)
    }

    val model = ChemicalFactory2(context)

    val maxAge = 60.seconds


    application {

        Window(title = "Chemical factory", onCloseRequest = ::exitApplication) {
            window.minimumSize = Dimension(400, 400)
            MaterialTheme {
                HorizontalSplitPane {
                    first(200.dp) {
                        Column(modifier = Modifier.background(color = Color.LightGray).fillMaxHeight().fillMaxWidth()) {
                            Row {
                                Text("Enable B producer", modifier = Modifier.align(Alignment.CenterVertically))

                                val checked by model.bProduction.subscribe().map { it.value > 0.0 }.collectAsState(true)
                                Checkbox(checked, onCheckedChange = {
                                    if (it) {
                                        model.bProduction.value = Numeric(2.0)
                                    } else {
                                        model.bProduction.value = Numeric(0.0)
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
                                    state = model.cProducer.production,
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Magenta))
                                )
                                PlotNumericState(
                                    context = context,
                                    state = model.bProducer.production,
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Red))
                                )
                                PlotNumericState(
                                    context = context,
                                    state = model.cBuffer.content,
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Black))
                                )
                                PlotNumericState(
                                    context = context,
                                    state = model.transformer.consumation,
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Green))
                                )
                                PlotNumericState(
                                    context = context,
                                    state = model.aProducer.production,
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Cyan))
                                )
                            }
                            Surface {
                                FlowLegend(6, label = {
                                    when (it) {
                                        0 -> {
                                            Text("Production", color = Color.Blue)
                                        }

                                        1 -> {
                                            Text("C Production", color = Color.Magenta)
                                        }

                                        2 -> {
                                            Text("B Production", color = Color.Red)
                                        }

                                        3 -> {
                                            Text("C Buffer", color = Color.Black)
                                        }

                                        4 -> {
                                            Text("Transformer C consumption", color = Color.Green)
                                        }

                                        5 -> {
                                            Text("A production", color = Color.Cyan)
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