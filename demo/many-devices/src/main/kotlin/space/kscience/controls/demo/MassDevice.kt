@file:OptIn(ExperimentalTime::class)

package space.kscience.controls.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.client.launchMagixService
import space.kscience.controls.client.magixFormat
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.spec.*
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.subscribe
import space.kscience.magix.rsocket.rSocketStreamWithTcp
import space.kscience.magix.server.RSocketMagixFlowPlugin
import space.kscience.magix.server.startMagixServer
import space.kscience.plotly.Plotly
import space.kscience.plotly.PlotlyConfig
import space.kscience.plotly.layout
import space.kscience.plotly.models.Bar
import space.kscience.plotly.models.invoke
import space.kscience.plotly.plotly
import space.kscience.visionforge.plotly.serveSinglePage
import space.kscience.visionforge.server.openInBrowser
import space.kscince.magix.zmq.ZmqMagixFlowPlugin
import space.kscince.magix.zmq.zmq
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime


class MassDeviceState(seed: Int = 0) {
    private val rng = Random(seed)

    private var counter: Long = 1

    val randomValue get() = rng.nextDouble()

    val incrementValue: Double get() = (counter++).toDouble()
}

object MassDevice : DeviceFactory<MassDeviceState>() {

    val value by doubleProperty { incrementValue }

    override suspend fun DeviceBase.createState(): MassDeviceState = MassDeviceState(meta["seed"].int ?: 0).also {
        doRecurring((meta["delay"].int ?: 5).milliseconds) {
            read(value)
        }
    }
}

suspend fun main() {
    val context = Context("Mass") {
        plugin(ClockManager) {
            "clock.mode" put "jvm"
        }
    }

    val clock = context.clock

    context.startMagixServer(
        RSocketMagixFlowPlugin(),
        ZmqMagixFlowPlugin()
    )

    val numDevices = 50


    repeat(numDevices) {
        delay(1.milliseconds)
        val deviceContext = Context("Device${it}") {
            plugin(DeviceManager)
        }

        val deviceManager = deviceContext.request(DeviceManager)

        deviceManager.install("device$it", MassDevice, Meta { "delay" put 5 })

        val endpointId = "device$it"
        val deviceEndpoint = MagixEndpoint.rSocketStreamWithTcp("localhost")
        deviceManager.launchMagixService(deviceEndpoint, endpointId)
    }

    val trace = Bar {
        context.launch(Dispatchers.IO) {
            val monitorEndpoint = MagixEndpoint.zmq("localhost")

            val mutex = Mutex()

            val latest = HashMap<String, Duration>()
            val max = HashMap<String, Duration>()

//            val counters = hashMapOf<String, Double>()

            monitorEndpoint.subscribe(DeviceManager.magixFormat).onEach { (magixMessage, payload) ->
                if (payload is PropertyChangedMessage) {
                    val delay = clock.now() - payload.time
                    mutex.withLock {
//                        val deviceName = payload.sourceDevice.toString()
//                        counters[deviceName] = counters[deviceName]?.inc() ?: 1.0
//                        println("${deviceName}:${counters[deviceName]!! - payload.value.double!!}")
                        latest[magixMessage.sourceEndpoint] = delay
                        max[magixMessage.sourceEndpoint] = maxOf(delay, max[magixMessage.sourceEndpoint] ?: ZERO)
                    }
                }
            }.launchIn(this)

            while (isActive) {
                delay(1000)
                mutex.withLock {
                    val sorted = max.mapKeys { it.key.substring(6).toInt() }.toSortedMap()
                    latest.clear()
                    max.clear()
                    x.numbers = sorted.keys
                    y.numbers = sorted.values.map { it.inWholeMicroseconds.toDouble() / 1000.0 }
                }
            }
        }
    }

    val application = Plotly.serveSinglePage(port = 9091, routeConfiguration = {
        updateInterval = 1000
    }) {
        vision {
            plotly(config = PlotlyConfig { saveAsSvg() }) {
                layout {
//                    title = "Latest event"

                    xaxis.title = "Device number"
                    yaxis.title = "Maximum latency in ms"
                }
                traces(trace)
            }
        }
    }


    application.openInBrowser()

    while (readlnOrNull().isNullOrBlank()) {

    }
}
