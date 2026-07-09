package space.kscience.controls.demo.constructor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.compose.letsplot.PlotNumericState
import space.kscience.controls.compose.letsplot.TimeSeriesPlot
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.units.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.models.continuous.*
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import java.awt.Dimension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private class ChemicalFactory2(
    context: Context
) : ContinuousFlowModel(context) {
    val aProduction = MutableValueState(AmountPerSecond<Kilograms>(4.0))
    val aProducer = producer(Kilograms, aProduction)

    val bProduction = MutableValueState(AmountPerSecond<Kilograms>(2.0))
    val bProducer = producer(Kilograms,bProduction)

    val joinAB = mix(Kilograms, listOf("a", "b")).apply {
        connectProducer("a", aProducer)
        connectProducer("b", bProducer)
    }

    val cProduction = MutableValueState(AmountPerSecond<CubicMeters>(2.5)).apply {
//        onTimer(0.2.seconds) { _, _ ->
//            value = Numeric(3.0 + Random.nextDouble(-0.1, 0.1))
//        }
    }

    val cProducer = producer(CubicMeters, cProduction)

    val cBuffer = buffer(CubicMeters, NumericAmount(10.0)).apply {
        connectProducer(cProducer)

        debugState("C buffer level", content)
        debugState("C buffer request", consumationCapacity)
        debugState("C buffer consumption", consumation)
        debugState("C buffer production", production)
    }

    val transformer = linearTransformer(
        consumerAlgebra = CubicMeters,
        producerAlgebra = Kilograms,
        production = 1.kilograms.perSecond
    ).apply {
        connectProducer(cBuffer)
    }

    val reactor = reaction(
        algebra = Kilograms,
        formula = mapOf("ab" to 0.66, "c" to 0.33),
        production = 1.kilograms.perSecond,
    ).apply {
        connectProducer("ab", joinAB)
        connectProducer("c", transformer)
        debugState("Reactor consumation request AB", individualConsumationCapacity["ab"]!!)
        debugState("Reactor consumation requesst C", individualConsumationCapacity["c"]!!)
    }

    val consumation = MutableValueState(AmountPerSecond<Kilograms>(10.0))

    val consumer = consumer(Kilograms, consumation).apply {
        connectProducer(reactor)
        debugState("consumation", consumation)
    }
}

@OptIn(ExperimentalSplitPaneApi::class)
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
                                        model.bProduction.value = AmountPerSecond(2.0)
                                    } else {
                                        model.bProduction.value = AmountPerSecond(0.0)
                                    }
                                })
                            }
                        }

                    }
                    second(400.dp) {
                        TimeSeriesPlot(
                            modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                            xAxisTitle = "Time",
                            yAxisTitle = "Value"
                        ) {
                            PlotNumericState(
                                context = context,
                                state = model.consumer.consumation,
                                name = "Production",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = model.cProducer.production,
                                name = "C Production",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = model.bProducer.production,
                                name = "B Production",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = model.cBuffer.content,
                                name = "C Buffer",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = model.transformer.consumation,
                                name = "Transformer C consumption",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = model.aProducer.production,
                                name = "A production",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                        }
                    }
                }
            }
        }
    }
}