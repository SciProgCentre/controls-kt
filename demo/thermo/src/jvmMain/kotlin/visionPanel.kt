package center.sciprog.controls.demo.thermo

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.css.CssBuilder
import kotlinx.css.height
import kotlinx.css.pct
import kotlinx.html.div
import kotlinx.serialization.json.Json
import space.kscience.controls.api.onPropertyChange
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.controls.vision.plotDeviceProperty
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaSerializer
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.asName
import space.kscience.plotly.Plotly
import space.kscience.plotly.PlotlyConfig
import space.kscience.plotly.PlotlyPlugin
import space.kscience.plotly.layout
import space.kscience.visionforge.VisionManager
import space.kscience.visionforge.html.VisionPage
import space.kscience.visionforge.server.visionPage
import space.kscience.visionforge.setAsRoot
import space.kscience.visionforge.visionManager
import kotlin.time.Duration.Companion.seconds

private suspend inline fun ApplicationCall.respondCss(builder: CssBuilder.() -> Unit) {
    this.respondText(CssBuilder().apply(builder).toString(), ContentType.Text.CSS)
}


suspend fun main(): Unit = coroutineScope {

    val context = Context {
        plugin(DeviceManager)
        plugin(ClockManager)
        plugin(VisionManager)
        plugin(PlotlyPlugin)
        plugin(ThermoSensorPlugin)
    }

    val config = generateTestConfig(4, 4)

    context.launchModbusSimulator(config)

    val thermoHub = context.ThermoSensorHub(config)

    val visionOfHub = VisionOfThermoSensorHub().apply {
        setAsRoot(context.visionManager)

        val mutex = Mutex()

        sensorConfig = config.sensors.mapValues {
            ThermoSensorAnalyzerConfig.combine(it.value.analyzer, config.analyzerDefault)
        }

        thermoHub.sensors.forEach { (name, sensor) ->
            sensor.onPropertyChange {
                mutex.withLock {
                    sensorData += name to ThermoSensorVisionData(
                        normalize(sensor.averageTemperature.value),
                        sensor.status.value,
                    )
                }
            }
        }

    }


    val plot = Plotly.plot {
        setAsRoot(context.visionManager)

        config.sensors.filter { it.value.showPlot }.forEach { (sensorName, sensorConfig) ->
            plotDeviceProperty(
                thermoHub.sensors.getValue(sensorName).sensor,
                ThermoSensorSpec.temperature,
                config.plot.period.seconds
            ) {
                name = sensorName
            }
        }

        layout {
            yaxis {
                title = "Temperature"
            }
            legend {
                meta["orientation"] = "h"
            }
        }
    }

    context.embeddedServer(CIO, port = 7080) {
        routing {
            staticResources("js", "js", null)
            staticResources("css", "css", null)
        }

        routing {
            route("css") {
                get("thermo.css") {
                    call.respondCss {
                        rule(".js-plotly-plot") {
                            height = 100.pct
                        }
                    }
                }
            }

            get("config.json") {
                call.respondText(contentType = ContentType.Application.Json) {
                    @Suppress("JSON_FORMAT_REDUNDANT")
                    Json {
                        prettyPrint = true
                    }.encodeToString(MetaSerializer, config.meta)
                }
            }
        }

        visionPage(
            context.visionManager,
            VisionPage.scriptHeader("js/thermo-vision.js"),
            VisionPage.styleSheetHeader("css/thermo.css"),
            routeConfiguration = {
                updateInterval = 1000
            }
        ) {
            div("container-fluid overflow-hidden") {
                div("row") {
                    div("col-md-3 vh-100 overflow-auto") {
                        vision(visionOfHub)
                    }

                    div("col-md-9 vh-100") {

                        val plotlyConfig = PlotlyConfig {
                            responsive = true
                        }

                        vision(vision = plot, name = "plot".asName(), outputMeta = plotlyConfig.meta)
                    }
                }
            }
        }

    }.start(true)
}