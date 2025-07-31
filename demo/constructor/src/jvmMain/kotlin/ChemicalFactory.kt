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
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.models.continuous.*
import space.kscience.controls.constructor.units.CubicMeters
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.constructor.units.NumericAmountAlgebra
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import java.awt.Dimension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant

class ChemicalFactory(
    context: Context
) : ContinuousFlowModel(context) {

    val aProduction = MutableDeviceState(Numeric<Kilograms>(1.0))
    val aProducer = producer(aProduction).apply {
        debugState("A production", production)
        debugState("A consumer request", consumerRequest)
    }

    val bProduction = MutableDeviceState(Numeric<Kilograms>(1.5))
    val bProducer = producer(bProduction).apply {
        debugState("B production", production)
    }

    val mixer = mix(kilograms, setOf("a", "b")).apply {
        connectProducer("a", aProducer)
        connectProducer("b", bProducer)
    }.apply {
        debugState("Mixer production", production)
        debugState("Mixer capacity", productionCapacity)
        debugState("Mixer consume request", consumerRequest)
        debugState("Mixer A request", asConsumer("a").consumationCapacity)
        debugState("Mixer B request", asConsumer("b").consumationCapacity)
        debugState("Mixer B consumation", individualConsumation["b"]!!)
        debugState("Mixer A consumation", individualConsumation["a"]!!)
        debugState("Mixer B consumation", individualConsumation["b"]!!)
    }

    val abBuffer = buffer(kilograms, Numeric(10.0)).apply {
        connectProducer(mixer)
        debugState("AB buffer supply request", supplyRequest)
        debugState("AB buffer consumer request", consumerRequest)
        debugState("AB buffer", content)
    }

    val cProduction = DeviceState(Numeric<CubicMeters>(5.0))
    val cProducer = producer(cProduction).apply {
        debugState("C production", production)
    }

    val cBuffer = buffer(cubicMeters, Numeric(50.0)).apply {
        connectProducer(cProducer)
        debugState("C buffer", content)
    }

    val converter = linearTransformer(cubicMeters, kilograms, Numeric(0.2)).apply {
        connectProducer(cBuffer)
    }.apply {
        debugState("Converter production", production)
    }


//    val reactor = reaction(kilograms, mapOf("ab" to Numeric(1.0), "c" to Numeric(1.0))).apply {
//        connectProducer("ab", abBuffer)
//        connectProducer("c", converter)
//
//        debugState("Reactor AB request", asConsumer("ab").consumationCapacity)
//        debugState("Reactor production", production)
//        debugState("Reactor consume request", consumerRequest)
//    }

    val reactor = mix(kilograms, setOf("ab", "c")).apply {
        connectProducer("ab", abBuffer)
        connectProducer("c", converter)
    }


    val consumer = consumer(kilograms, DeviceState(Numeric(2.0))).apply {
        connectProducer(reactor)

        debugState("Consumer consumation", consumation)
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

    val model = ChemicalFactory(context)

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
                                        model.bProduction.value = Numeric(1.5)
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
                                    state = model.abBuffer.content,
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Black))
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
                            }
                            Surface {
                                FlowLegend(4, label = {
                                    when (it) {
                                        0 -> {
                                            Text("Production", color = Color.Blue)
                                        }

                                        1 -> {
                                            Text("AB Buffer level", color = Color.Black)
                                        }

                                        2 -> {
                                            Text("C Production", color = Color.Magenta)
                                        }

                                        3 -> {
                                            Text("B Production", color = Color.Red)
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