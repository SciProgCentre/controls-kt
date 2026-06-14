package space.kscience.controls.demo.constructor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.api.resolveDevice
import space.kscience.controls.compose.letsplot.PlotNumericState
import space.kscience.controls.compose.letsplot.TimeSeriesPlot
import space.kscience.controls.constructor.models.continuous.ContinuousBuffer
import space.kscience.controls.constructor.models.continuous.ContinuousConsumer
import space.kscience.controls.constructor.models.continuous.ContinuousModelLibrary
import space.kscience.controls.constructor.models.continuous.ContinuousProducer
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.NumericAmount
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import java.awt.Dimension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSplitPaneApi::class)
fun main() {
    val context = Context {
        plugin(DeviceManager)
        plugin(ClockManager)
    }

    val deviceManager = context.plugins[DeviceManager]!!

    val config = Json.decodeFromStream<Meta>({}.javaClass.getResourceAsStream("/ChemicalFactory.json"))

    val model = deviceManager.install(config, ContinuousModelLibrary(Kilograms).factories)


    //FIXME add properties to flow model for observability
    val consumer = model.resolveDevice("consumer") as ContinuousConsumer<Kilograms, NumericAmount<Kilograms>>
    val abBuffer = model.resolveDevice("abBuffer") as ContinuousBuffer<Kilograms, NumericAmount<Kilograms>>
    val cProducer = model.resolveDevice("cProducer") as ContinuousProducer<Kilograms, NumericAmount<Kilograms>>
    val bProducer = model.resolveDevice("bProducer") as ContinuousProducer<Kilograms, NumericAmount<Kilograms>>
    val aProducer = model.resolveDevice("aProducer") as ContinuousProducer<Kilograms, NumericAmount<Kilograms>>

    val maxAge = 60.seconds


    application {

        Window(title = "Chemical factory", onCloseRequest = ::exitApplication) {
            window.minimumSize = Dimension(400, 400)
            MaterialTheme {
                HorizontalSplitPane {
                    first(200.dp) {
                        Column(modifier = Modifier.background(color = Color.LightGray).fillMaxHeight().fillMaxWidth()) {
//                            Row {
//                                Text("Enable B producer", modifier = Modifier.align(Alignment.CenterVertically))
//
//                                val checked by bProducer.production.subscribe().map { it.value > 0.0 }.collectAsState(true)
//                                Checkbox(checked, onCheckedChange = {
//                                    if (it) {
//                                        model.bProduction.value = AmountPerSecond(1.5)
//                                    } else {
//                                        model.bProduction.value = AmountPerSecond(0.0)
//                                    }
//                                })
//                            }
//                            Row {
//                                Text("Enable C producer", modifier = Modifier.align(Alignment.CenterVertically))
//
//                                val checked by cProducer.production.subscribe().map { it.value > 0.0 }.collectAsState(true)
//                                Checkbox(checked, onCheckedChange = {
//                                    if (it) {
//                                        model.cProduction.value = AmountPerSecond(10.0)
//                                    } else {
//                                        model.cProduction.value = AmountPerSecond(0.0)
//                                    }
//                                })
//                            }
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
                                state = consumer.consumation,
                                name = "Production",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = abBuffer.content,
                                name = "AB Buffer level",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = cProducer.production,
                                name = "C Production",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = bProducer.production,
                                name = "B Production",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = aProducer.production,
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