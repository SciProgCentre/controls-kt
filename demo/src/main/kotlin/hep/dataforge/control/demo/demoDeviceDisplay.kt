package hep.dataforge.control.demo

import hep.dataforge.meta.double
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import scientifik.plotly.Plotly
import scientifik.plotly.layout
import scientifik.plotly.models.Trace
import scientifik.plotly.server.PlotlyServer
import scientifik.plotly.server.pushUpdates
import scientifik.plotly.server.serve
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

fun CoroutineScope.servePlots(device: DemoDevice): PlotlyServer  {
    val sinFlow = device.sin.flow()
    val cosFlow = device.cos.flow()
    val sinCosFlow = sinFlow.zip(cosFlow) { sin, cos ->
        sin.double!! to cos.double!!
    }

    return Plotly.serve(this) {
        plot(rowNumber = 0, colOrderNumber = 0, size = 6) {
            layout {
                title = "sin property"
                xaxis.title = "point index"
                yaxis.title = "sin"
            }
            trace {
                this@servePlots.launch {
                    val flow: Flow<Iterable<Double>> = sinFlow.mapNotNull { it.double }.windowed(100)
                    updateFrom(Trace.Y_AXIS, flow)
                }
            }
        }
        plot(rowNumber = 0, colOrderNumber = 1, size = 6) {
            layout {
                title = "cos property"
                xaxis.title = "point index"
                yaxis.title = "cos"
            }
            trace {
                this@servePlots.launch {
                    val flow: Flow<Iterable<Double>> = cosFlow.mapNotNull { it.double }.windowed(100)
                    updateFrom(Trace.Y_AXIS, flow)
                }
            }
        }
        plot(rowNumber = 1, colOrderNumber = 0, size = 12) {
            layout {
                title = "cos vs sin"
                xaxis.title = "sin"
                yaxis.title = "cos"
            }
            trace {
                name = "non-synchronized"
                this@servePlots.launch {
                    val flow: Flow<Iterable<Pair<Double, Double>>> = sinCosFlow.windowed(30)
                    updateXYFrom(flow)
                }
            }
        }
    }.pushUpdates()
}
