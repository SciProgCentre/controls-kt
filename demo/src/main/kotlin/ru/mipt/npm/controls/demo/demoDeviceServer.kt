package ru.mipt.npm.controls.demo

import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.html.div
import kotlinx.html.link
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.rsocket.rSocketWithWebSockets
import space.kscience.dataforge.meta.MetaItem
import space.kscience.dataforge.meta.double
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


suspend fun startDemoDeviceServer(magixHost: String = "localhost"): ApplicationEngine = embeddedServer(CIO, 8080) {
    val sinFlow = MutableSharedFlow<MetaItem?>()// = device.sin.flow()
    val cosFlow = MutableSharedFlow<MetaItem?>()// = device.cos.flow()

    launch {
        val endpoint = MagixEndpoint.rSocketWithWebSockets(magixHost, DeviceMessage.serializer())
        endpoint.subscribe().collect { magix ->
            (magix.payload as? PropertyChangedMessage)?.let { message ->
                when (message.property) {
                    "sin" -> sinFlow.emit(message.value)
                    "cos" -> cosFlow.emit(message.value)
                }
            }
        }
    }

    plotlyModule("plots").apply {
        updateMode = PlotlyUpdateMode.PUSH
        updateInterval = 50
    }.page { container ->
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
                            val flow: Flow<Iterable<Pair<Double, Double>>> = sinCosFlow.windowed(30)
                            updateXYFrom(flow)
                        }
                    }
                }
            }
        }
    }
}

