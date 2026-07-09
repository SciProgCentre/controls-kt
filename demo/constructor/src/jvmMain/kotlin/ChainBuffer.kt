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
import space.kscience.controls.constructor.units.AmountPerSecond
import space.kscience.controls.constructor.units.CubicMeters
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.NumericAmount
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.models.continuous.*
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import java.awt.Dimension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private class ChainBufferModel(
    context: Context
) : ContinuousFlowModel(context) {
    val production = MutableValueState(AmountPerSecond<Kilograms>(1.0))
    val producer = producer(Kilograms, production).apply {
        debugState("Producer production", production)
    }

    val buffer1 = buffer(Kilograms, NumericAmount(10.0)).apply {
        connectProducer(producer)
    }

    val transformer = linearTransformer(
        consumerAlgebra = Kilograms,
        producerAlgebra = CubicMeters,
        production = AmountPerSecond(1.0)
    ).apply {
        connectProducer(buffer1)
    }

    val buffer2 = buffer(CubicMeters, NumericAmount(10.0)).apply {
        connectProducer(transformer.limited(this, AmountPerSecond(2.0)))
        debugState("Buffer 2", content)
    }

    val consumation = MutableValueState(AmountPerSecond<CubicMeters>(2.0))

    val consumer = consumer(CubicMeters, consumation).apply {
        connectProducer(buffer2)
        debugState("Consumer consumption", consumation)
    }
}

@OptIn(ExperimentalSplitPaneApi::class)
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
                                        model.production.value = AmountPerSecond(1.0)
                                    } else {
                                        model.production.value = AmountPerSecond(0.0)
                                    }
                                })
                            }
                            Row {
                                Text("Enable consumer", modifier = Modifier.align(Alignment.CenterVertically))

                                val checked by model.consumation.subscribe().map { it.value > 0.0 }.collectAsState(true)
                                Checkbox(checked, onCheckedChange = {
                                    if (it) {
                                        model.consumation.value = AmountPerSecond(2.0)
                                    } else {
                                        model.consumation.value = AmountPerSecond(0.0)
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
                                name = "Total product",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = model.buffer1.content,
                                name = "Buffer 1",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = model.transformer.production,
                                name = "Transformer production",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = model.buffer2.content,
                                name = "Buffer 2",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = model.producer.production,
                                name = "Producer production",
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