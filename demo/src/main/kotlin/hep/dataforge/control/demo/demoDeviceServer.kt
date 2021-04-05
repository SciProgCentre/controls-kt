package space.kscience.dataforge.control.demo

import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.html.div
import kotlinx.html.link
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.control.controllers.devices
import space.kscience.dataforge.control.server.startDeviceServer
import space.kscience.dataforge.control.server.whenStarted
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.NameToken
import space.kscience.plotly.layout
import space.kscience.plotly.models.Trace
import space.kscience.plotly.plot
import space.kscience.plotly.server.PlotlyUpdateMode
import space.kscience.plotly.server.plotlyModule
import space.kscience.plotly.trace
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


fun startDemoDeviceServer(context: Context, device: DemoDevice): ApplicationEngine {
    context.devices.registerDevice(NameToken("demo"), device)
    val server = context.startDeviceServer(context.devices)
    server.whenStarted {
        plotlyModule("plots").apply {
            updateMode = PlotlyUpdateMode.PUSH
            updateInterval = 50
        }.page { container ->
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
                    plot(renderer = container) {
                        layout {
                            title = "sin property"
                            xaxis.title = "point index"
                            yaxis.title = "sin"
                        }
                        trace {
                            context.launch {
                                val flow: Flow<Iterable<Double>> = sinFlow.mapNotNull { it.double }.windowed(100)
                                updateFrom(Trace.Y_AXIS, flow)
                            }
                        }
                    }
                }
                div("col-6") {
                    plot(renderer = container) {
                        layout {
                            title = "cos property"
                            xaxis.title = "point index"
                            yaxis.title = "cos"
                        }
                        trace {
                            context.launch {
                                val flow: Flow<Iterable<Double>> = cosFlow.mapNotNull { it.double }.windowed(100)
                                updateFrom(Trace.Y_AXIS, flow)
                            }
                        }
                    }
                }
            }
            div("row") {
                div("col-12") {
                    plot(renderer = container) {
                        layout {
                            title = "cos vs sin"
                            xaxis.title = "sin"
                            yaxis.title = "cos"
                        }
                        trace {
                            name = "non-synchronized"
                            context.launch {
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

