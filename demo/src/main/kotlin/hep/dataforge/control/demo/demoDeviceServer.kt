package hep.dataforge.control.demo

import hep.dataforge.control.server.startDeviceServer
import hep.dataforge.control.server.whenStarted
import hep.dataforge.meta.double
import io.ktor.application.uninstall
import io.ktor.server.engine.ApplicationEngine
import io.ktor.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.html.div
import kotlinx.html.link
import scientifik.plotly.layout
import scientifik.plotly.models.Trace
import scientifik.plotly.plot
import scientifik.plotly.server.PlotlyServerConfig
import scientifik.plotly.server.PlotlyUpdateMode
import scientifik.plotly.server.plotlyModule
import scientifik.plotly.trace
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * In-place replacement for absent method from stdlib
 */
fun <T> Flow<T>.windowed(size: Int): Flow<Iterable<T>> {
    val queue = ConcurrentLinkedQueue<T>()
    return flow {
        this@windowed.collect {
            queue.add(it)
            if (queue.size >= size) {
                queue.poll()
            }
            emit(queue)
        }
    }
}

suspend fun Trace.updateFrom(axisName: String, flow: Flow<Iterable<Double>>) {
    flow.collect {
        axis(axisName).numbers = it
    }
}

suspend fun Trace.updateXYFrom(flow: Flow<Iterable<Pair<Double, Double>>>) {
    flow.collect { pairs ->
        x.numbers = pairs.map { it.first }
        y.numbers = pairs.map { it.second }
    }
}



fun CoroutineScope.startDemoDeviceServer(device: DemoDevice): ApplicationEngine {
    val server = startDeviceServer(mapOf("demo" to device))
    server.whenStarted {
        uninstall(WebSockets)
        plotlyModule(
            "plots",
            PlotlyServerConfig { updateMode = PlotlyUpdateMode.PUSH; updateInterval = 50 }
        ) { container ->
            val sinFlow = device.sin.flow()
            val cosFlow = device.cos.flow()
            val sinCosFlow = sinFlow.zip(cosFlow) { sin, cos ->
                sin.double!! to cos.double!!
            }
            link {
                rel = "stylesheet"
                href = "https://stackpath.bootstrapcdn.com/bootstrap/4.5.0/css/bootstrap.min.css"
                attributes["integrity"] = "sha384-9aIt2nRpC12Uk9gS9baDl411NQApFmC26EwAOH8WgZl5MYYxFfc+NcPb1dKGj7Sk"
                attributes["crossorigin"] = "anonymous"
            }
            div("row") {
                div("col-6") {
                    plot(container = container) {
                        layout {
                            title = "sin property"
                            xaxis.title = "point index"
                            yaxis.title = "sin"
                        }
                        trace {
                            launch {
                                val flow: Flow<Iterable<Double>> = sinFlow.mapNotNull { it.double }.windowed(100)
                                updateFrom(Trace.Y_AXIS, flow)
                            }
                        }
                    }
                }
                div("col-6") {
                    plot(container = container) {
                        layout {
                            title = "cos property"
                            xaxis.title = "point index"
                            yaxis.title = "cos"
                        }
                        trace {
                            launch {
                                val flow: Flow<Iterable<Double>> = cosFlow.mapNotNull { it.double }.windowed(100)
                                updateFrom(Trace.Y_AXIS, flow)
                            }
                        }
                    }
                }
            }
            div("row") {
                div("col-12") {
                    plot(container = container) {
                        layout {
                            title = "cos vs sin"
                            xaxis.title = "sin"
                            yaxis.title = "cos"
                        }
                        trace {
                            name = "non-synchronized"
                            launch {
                                val flow: Flow<Iterable<Pair<Double, Double>>> = sinCosFlow.windowed(30)
                                updateXYFrom(flow)
                            }
                        }
                    }
                }
            }
        }
    }
    return server
}

