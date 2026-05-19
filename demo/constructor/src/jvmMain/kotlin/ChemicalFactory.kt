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
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.compose.letsplot.PlotNumericState
import space.kscience.controls.compose.letsplot.TimeSeriesPlot
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.models.continuous.*
import space.kscience.controls.constructor.units.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import java.awt.Dimension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ChemicalFactory(
    context: Context
) : ContinuousFlowModel(context) {

    val aProduction = MutableValueState(AmountPerSecond<Kilograms>(1.0))
    val aProducer = producer(Kilograms, aProduction)

    val bProduction = MutableValueState(AmountPerSecond<Kilograms>(1.5))
    val bProducer = producer(Kilograms, bProduction)

    val mixer = mix(Kilograms, setOf("a", "b")).apply {
        connectProducer("a", aProducer)
        connectProducer("b", bProducer)
    }

    val abBuffer = buffer(Kilograms, NumericAmount(10.0)).apply {
        connectProducer(mixer)
        debugState("AB buffer", content)
    }

    val cProduction = MutableValueState(AmountPerSecond<CubicMeters>(10.0))
    val cProducer = producer(CubicMeters, cProduction)

    val cBuffer = buffer(CubicMeters, NumericAmount(50.0)).apply {
        connectProducer(cProducer)
        debugState("C buffer", content)
    }

    val converter = linearTransformer(CubicMeters, Kilograms, AmountPerSecond(0.2)).apply {
        connectProducer(cBuffer)
    }

    val reactor = reaction(
        algebra = Kilograms,
        formula = mapOf("ab" to 1.0, "c" to 1.0),
        production = 1.kilograms.perSecond
    ).apply {
        connectProducer("ab", abBuffer)
        connectProducer("c", converter)
    }


    val consumer = consumer(Kilograms, ValueState(AmountPerSecond(2.0))).apply {
        connectProducer(reactor)

        debugState("Consumer consumation", consumation)
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
                                        model.bProduction.value = AmountPerSecond(1.5)
                                    } else {
                                        model.bProduction.value = AmountPerSecond(0.0)
                                    }
                                })
                            }
                            Row {
                                Text("Enable C producer", modifier = Modifier.align(Alignment.CenterVertically))

                                val checked by model.cProduction.subscribe().map { it.value > 0.0 }.collectAsState(true)
                                Checkbox(checked, onCheckedChange = {
                                    if (it) {
                                        model.cProduction.value = AmountPerSecond(10.0)
                                    } else {
                                        model.cProduction.value = AmountPerSecond(0.0)
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
                                state = model.abBuffer.content,
                                name = "AB Buffer level",
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
                                state = model.aProducer.production,
                                name = "A Production",
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