package space.kscience.controls.demo

import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.html.div
import kotlinx.html.link
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.client.magixFormat
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.spec.name
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.subscribe
import space.kscience.plotly.Plotly
import space.kscience.plotly.layout
import space.kscience.plotly.models.Trace
import space.kscience.plotly.plot
import space.kscience.plotly.server.PlotlyUpdateMode
import space.kscience.plotly.server.serve
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


fun CoroutineScope.startDemoDeviceServer(magixEndpoint: MagixEndpoint): ApplicationEngine {
    //share subscription to a parse message only once
    val subscription = magixEndpoint.subscribe(DeviceManager.magixFormat).shareIn(this, SharingStarted.Lazily)

    val sinFlow = subscription.mapNotNull { (_, payload) ->
        (payload as? PropertyChangedMessage)?.takeIf { it.property == DemoDevice.sin.name }
    }.map { it.value }

    val cosFlow = subscription.mapNotNull { (_, payload) ->
        (payload as? PropertyChangedMessage)?.takeIf { it.property == DemoDevice.cos.name }
    }.map { it.value }

    val sinCosFlow = subscription.mapNotNull { (_, payload) ->
        (payload as? PropertyChangedMessage)?.takeIf { it.property == DemoDevice.coordinates.name }
    }.map { it.value }

    return Plotly.serve(port = 9091, scope = this) {
        updateMode = PlotlyUpdateMode.PUSH
        updateInterval = 100
        page { container ->
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
                            launch {
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
                    plot(renderer = container) {
                        layout {
                            title = "cos vs sin"
                            xaxis.title = "sin"
                            yaxis.title = "cos"
                        }
                        trace {
                            name = "non-synchronized"
                            launch {
                                val flow: Flow<Iterable<Pair<Double, Double>>> = sinCosFlow.mapNotNull {
                                    it["x"].double!! to it["y"].double!!
                                }.windowed(30)
                                updateXYFrom(flow)
                            }
                        }
                    }
                }
            }

        }
    }

}

