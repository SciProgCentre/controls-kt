package space.kscience.controls.demo.constructor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.compose.letsplot.PlotNumericState
import space.kscience.controls.compose.letsplot.TimeSeriesPlot
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.models.continuous.*
import space.kscience.controls.constructor.units.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import java.awt.Dimension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


private class EnrichmentFacility(
    context: Context,
) : ContinuousFlowModel(context) {

    val mixture = MixtureAlgebra(Kilograms)

    val productionValue = MutableValueState(AmountPerSecond<Kilograms>(1.0))

    val production = productionValue.map {
        with(mixture) {
            Mixture.ofFractions<Kilograms>(component1 to 1.0, component2 to 1.0) * it.value
        }.perSecond
    }

    val producer = producer(mixture, production)

    val mixer = mix(mixture, setOf("producer", "feedback")).apply {
        connectProducer("producer", producer)
    }

    private class MyMixtureSeparationRule(
        val fractions: Map<MixtureComponent, Map<String, Double>>,
    ) : SeparationRule<Kilograms, Mixture<Kilograms, NumericAmount<Kilograms>>> {
        override val productionKeys: Collection<String> = fractions.flatMap { it.value.keys }.distinct()

        private class TaggedFraction(
            val component: MixtureComponent,
            val output: String,
            val value: NumericAmount<Kilograms>
        )

        override fun forward(input: PerSecond<Kilograms, Mixture<Kilograms, NumericAmount<Kilograms>>>): Map<String, PerSecond<Kilograms, Mixture<Kilograms, NumericAmount<Kilograms>>>> {
            val entries = input.valuePerSecond.components.entries.flatMap { (component, inputValue) ->
                fractions[component]?.map { (key, fraction) ->
                    TaggedFraction(component, key, inputValue * fraction)
                } ?: emptyList()
            }

            return entries.groupBy { it.output }.mapValues { (outputKey, fractions) ->
                PerSecond(
                    Mixture(fractions.groupBy { it.component }
                        .mapValues { NumericAmount(it.value.sumOf { item -> item.value.value }) })
                )
            }
        }

        override fun backward(output: Map<String, AmountPerSecond<Kilograms>>): AmountPerSecond<Kilograms> =
            AmountPerSecond(output.values.sumOf { it.value })

    }

    val separator = separator(
        algebra = mixture,
        separationRule = MyMixtureSeparationRule(
            mapOf(
                component1 to mapOf(
                    productionKey to 0.7,
                    feedbackKey to 0.2,
                    discardKey to 0.1
                ),
                component2 to mapOf(
                    productionKey to 0.1,
                    feedbackKey to 0.2,
                    discardKey to 0.7
                )
            )
        )
    ).apply {
        connectProducer(mixer)
        mixer.connectProducer(feedbackKey, asProducer(feedbackKey).delayed(this, 200.milliseconds))
    }

    val discard = consumer(mixture, ValueState(AmountPerSecond(2.0))).apply {
        connectProducer(separator.asProducer(discardKey))
    }

    val consumption = MutableValueState(AmountPerSecond<Kilograms>(2.0))
    val consumer = consumer(mixture, consumption).apply {
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

@OptIn(ExperimentalSplitPaneApi::class)
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
                                Text("${it.valuePerSecond[EnrichmentFacility.component1]?.value}, ${it.valuePerSecond[EnrichmentFacility.component2]?.value}")
                            }

                            model.displayState("Production", model.consumer.consumation) {
                                Text("${it.valuePerSecond[EnrichmentFacility.component1]?.value}, ${it.valuePerSecond[EnrichmentFacility.component2]?.value}")
                            }

                            model.displayState("Refuse", model.discard.consumation) {
                                Text("${it.valuePerSecond[EnrichmentFacility.component1]?.value}, ${it.valuePerSecond[EnrichmentFacility.component2]?.value}")
                            }

                            model.displayState(
                                "Feedback",
                                model.mixer.individualConsumation[EnrichmentFacility.feedbackKey]!!
                            ) {
                                Text("${it.valuePerSecond[EnrichmentFacility.component1]?.value}, ${it.valuePerSecond[EnrichmentFacility.component2]?.value}")
                            }

                            model.displayState("Mixer production", model.mixer.production) {
                                Text("${it.valuePerSecond[EnrichmentFacility.component1]?.value}, ${it.valuePerSecond[EnrichmentFacility.component2]?.value}")
                            }

                            model.slider("Production", model.productionValue, 0f..4f)
                            model.slider("Consumption", model.consumption, 0f..4f)

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
                                state = model.consumer.consumation.map {
                                    it.valuePerSecond.components[EnrichmentFacility.component1] ?: NumericAmount(0.0)
                                },
                                name = "Component1 consumation",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = model.consumer.consumation.map {
                                    it.valuePerSecond.components[EnrichmentFacility.component2] ?: NumericAmount(0.0)
                                },
                                name = "Component2 consumation",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )

                            PlotNumericState(
                                context = context,
                                state = model.producer.production.map {
                                    it.valuePerSecond.components[EnrichmentFacility.component1] ?: NumericAmount(0.0)
                                },
                                name = "Component1 production",
                                maxAge = maxAge,
                                sampling = 500.milliseconds,
                            )
                            PlotNumericState(
                                context = context,
                                state = model.producer.production.map {
                                    it.valuePerSecond.components[EnrichmentFacility.component2] ?: NumericAmount(0.0)
                                },
                                name = "Componen2 production",
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