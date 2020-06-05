package hep.dataforge.control.demo

import hep.dataforge.meta.double
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import scientifik.plotly.Plotly
import scientifik.plotly.layout
import scientifik.plotly.models.Trace
import scientifik.plotly.server.pushUpdates
import scientifik.plotly.server.serve
import java.util.concurrent.ConcurrentLinkedQueue

fun main() {
    runBlocking(Dispatchers.Default) {
        val device = DemoDevice()

        val sinFlow = device.sin.flow()
        val cosFlow = device.cos.flow()
        val sinCosFlow = sinFlow.zip(cosFlow) { sin, cos ->
            sin.double to cos.double
        }

//        launch {
//            device.valueFlow().collect { (name, item) ->
//                if (name == "sin") {
//                    println("Device produced $item")
//                    println("Sin value is ${sinFlow.value}")
//                }
//            }
//        }
//
//        launch {
//            sinFlow.mapNotNull { it.double }.collect {
//                println("Device processed $it")
//            }
//        }

        val server = Plotly.serve(this) {
            plot(rowNumber = 0, colOrderNumber = 0, size = 6) {
                layout {
                    title = "sin property"
                    xaxis.title = "point index"
                    yaxis.title = "sin"
                }
                val trace = Trace.empty()
                data.add(trace)
                launch {
                    val queue = ConcurrentLinkedQueue<Double>()

                    sinFlow.mapNotNull { it.double }.collect {
                        queue.add(it)
                        if (queue.size >= 100) {
                            queue.poll()
                        }
                        trace.y.numbers = queue
                    }
                }
            }
            plot(rowNumber = 0, colOrderNumber = 1, size = 6) {
                layout {
                    title = "cos property"
                    xaxis.title = "point index"
                    yaxis.title = "cos"
                }
                val trace = Trace.empty()
                data.add(trace)
                launch {
                    val queue = ConcurrentLinkedQueue<Double>()

                    cosFlow.mapNotNull { it.double }.collect {
                        queue.add(it)
                        if (queue.size >= 100) {
                            queue.poll()
                        }
                        trace.y.numbers = queue
                    }
                }
            }
            plot(rowNumber = 1, colOrderNumber = 0, size = 12) {
                layout {
                    title = "cos vs sin"
                    xaxis.title = "sin"
                    yaxis.title = "cos"
                }
                val trace = Trace.empty()
                data.add(trace)
                launch {
                    val queue = ConcurrentLinkedQueue<Pair<Double, Double>>()

                    sinCosFlow.collect { pair ->
                        val x = pair.first ?: return@collect
                        val y = pair.second ?: return@collect
                        queue.add(x to y)
                        if (queue.size >= 20) {
                            queue.poll()
                        }
                        trace.x.numbers = queue.map { it.first }
                        trace.y.numbers = queue.map { it.second }
                    }
                }
            }



        }.pushUpdates()



        readLine()

        println("Stopping")
        server.stop()
        device.close()
    }
}