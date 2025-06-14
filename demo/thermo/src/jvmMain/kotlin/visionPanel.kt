package center.sciprog.controls.demo.thermo

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing
import kotlinx.coroutines.coroutineScope
import space.kscience.controls.api.onPropertyChange
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.plotly.PlotlyPlugin
import space.kscience.plotly.models.Scatter
import space.kscience.visionforge.VisionManager
import space.kscience.visionforge.html.VisionPage
import space.kscience.visionforge.server.visionPage
import space.kscience.visionforge.visionManager


suspend fun main(): Unit = coroutineScope {

    val context = Context {
        plugin(DeviceManager)
        plugin(ClockManager)
        plugin(VisionManager)
        plugin(PlotlyPlugin)
        plugin(ThermoSensorPlugin)
    }

    val config = generateTestConfig(1, 10)

    context.launchModbusSimulator(config)

    val thermoHub = context.ThermoSensorHub(config)

    val traces = config.sensors.filter { it.value.showPlot }.mapValues { (name, sensorConfig) ->
        Scatter()
    }

    val vision = VisionOfThermoSensorHub().apply {
        plot.traces(traces.values)
    }

    thermoHub.sensors.forEach { (name, sensor) ->
        sensor.onPropertyChange {
            vision.sensorData += name to ThermoSensorVisionData(sensor.temperature.value, sensor.status.value)

            if (property == ThermoSensorAnalyzer::temperature.name) {
                traces[name]?.apply {
                    x.strings += context.clock.now().toString()
                    y.numbers += sensor.temperature.value
                }
            }
        }
    }


    context.embeddedServer(CIO, port = 7777) {
        routing {
            staticResources("js", "js", null)
            staticResources("css", "css", null)
        }

        visionPage(
            context.visionManager,
            VisionPage.scriptHeader("js/thermo-vision.js"),
        ) {
            vision(vision)
        }


    }.start(true)
}