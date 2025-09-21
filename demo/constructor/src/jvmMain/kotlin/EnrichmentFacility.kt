package space.kscience.controls.demo.constructor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.remember
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
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.compose.PlotNumericState
import space.kscience.controls.compose.TimeAxisModel
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.map
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


private class EnrichmentFacility(
    context: Context
) : ContinuousFlowModel(context) {

    val mixture = MixtureAlgebra(Kilograms, Mixture.ofFractions(component1 to 1.0, component2 to 1.0))

    val production = MutableDeviceState(
        mixture.one
    )

    val producer = producer(mixture, production)

    val mixer = mix(mixture, setOf("producer", "feedback")).apply {
        connectProducer("producer", producer)
    }

    private class MyMixtureSeparationRule(
        val fractions: Map<MixtureComponent, Map<String, Double>>
    ) : SeparationRule<Kilograms, Mixture<Kilograms, Numeric<Kilograms>>> {
        override val productionKeys: Collection<String> = fractions.flatMap { it.value.keys }.distinct()

        private class TaggedFraction(val component: MixtureComponent, val output: String, val value: Numeric<Kilograms>)

        override fun forward(input: Mixture<Kilograms, Numeric<Kilograms>>): Map<String, Mixture<Kilograms, Numeric<Kilograms>>> {
            val entries = input.components.entries.flatMap { (component, inputValue) ->
                fractions[component]?.map { (key, fraction) ->
                    TaggedFraction(component, key, inputValue * fraction)
                } ?: emptyList()
            }

            return entries.groupBy { it.output }.mapValues { (outputKey, fractions) ->
                Mixture(fractions.groupBy { it.component }
                    .mapValues { Numeric(it.value.sumOf { item -> item.value.value }) })
            }
        }

        override fun backward(output: Map<String, Numeric<Kilograms>>): Numeric<Kilograms> =
            Numeric(output.values.sumOf { it.value })

    }

    val separator = separator(
        algebra = mixture,
        separationRule = MyMixtureSeparationRule(
            mapOf(
                component1 to mapOf(
                    productionKey to 0.75,
                    feedbackKey to 0.2,
                    discardKey to 0.05
                ),
                component2 to mapOf(
                    productionKey to 0.1,
                    feedbackKey to 0.1,
                    discardKey to 0.8
                )
            )
        )
    ).apply {
        connectProducer(mixer)
        mixer.connectProducer(feedbackKey, asProducer(feedbackKey).delayed(this, 400.milliseconds))
    }

    val discard = consumer(mixture, DeviceState(Numeric(2.0))).apply {
        connectProducer(separator.asProducer(discardKey))
    }

    val consumer = consumer(mixture, DeviceState(Numeric(2.0))).apply {
        connectProducer(separator.asProducer(productionKey))
    }

    companion object {
        val component1 = MixtureComponent("component1")
        val component2 = MixtureComponent("component2")

        val productionKey = "production"
        val feedbackKey = "feedback"
        val discardKey = "discard"

    }
}

@OptIn(ExperimentalKoalaPlotApi::class, ExperimentalSplitPaneApi::class)
fun main() {
    val context = Context {
        plugin(DeviceManager)
        plugin(ClockManager)
    }

    val model = EnrichmentFacility(context)

    val maxAge = 60.seconds


    application {

        Window(title = "Chemical factory", onCloseRequest = ::exitApplication) {
            window.minimumSize = Dimension(400, 400)
            MaterialTheme {
                HorizontalSplitPane {
                    first(200.dp) {
                        Column(modifier = Modifier.background(color = Color.LightGray).fillMaxHeight().fillMaxWidth()) {
                            model.displayState("Source", model.producer.production) {
                                Text("${it[EnrichmentFacility.component1]?.value}, ${it[EnrichmentFacility.component2]?.value}")
                            }

                            model.displayState("Production", model.consumer.consumation) {
                                Text("${it[EnrichmentFacility.component1]?.value}, ${it[EnrichmentFacility.component2]?.value}")
                            }

                            model.displayState("Refuse", model.discard.consumation) {
                                Text("${it[EnrichmentFacility.component1]?.value}, ${it[EnrichmentFacility.component2]?.value}")
                            }

                            model.displayState("Feedback", model.mixer.individualConsumation[EnrichmentFacility.feedbackKey]!!) {
                                Text("${it[EnrichmentFacility.component1]?.value}, ${it[EnrichmentFacility.component2]?.value}")
                            }

                            model.displayState("Mixer production", model.mixer.production) {
                                Text("${it[EnrichmentFacility.component1]?.value}, ${it[EnrichmentFacility.component2]?.value}")
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
                                    state = model.consumer.consumation.map {
                                        it.components[EnrichmentFacility.component1] ?: Numeric(0.0)
                                    },
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Blue))
                                )
                                PlotNumericState(
                                    context = context,
                                    state = model.consumer.consumation.map {
                                        it.components[EnrichmentFacility.component2] ?: Numeric(0.0)
                                    },
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Red))
                                )

                                PlotNumericState(
                                    context = context,
                                    state = model.producer.production.map {
                                        it.components[EnrichmentFacility.component1] ?: Numeric(0.0)
                                    },
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Green))
                                )
                                PlotNumericState(
                                    context = context,
                                    state = model.producer.production.map {
                                        it.components[EnrichmentFacility.component2] ?: Numeric(0.0)
                                    },
                                    maxAge = maxAge,
                                    sampling = 500.milliseconds,
                                    lineStyle = LineStyle(SolidColor(Color.Black))
                                )
                            }
                            Surface {
                                FlowLegend(4, label = {
                                    when (it) {
                                        0 -> {
                                            Text("Component1 consumation", color = Color.Blue)
                                        }

                                        1 -> {
                                            Text("Component2 consumation", color = Color.Red)
                                        }

                                        2 -> {
                                            Text("Component1 production", color = Color.Green)
                                        }

                                        3 -> {
                                            Text("Componen2 production", color = Color.Black)
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