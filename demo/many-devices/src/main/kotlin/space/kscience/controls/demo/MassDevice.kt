package space.kscience.controls.demo

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import space.kscience.controls.client.launchMagixService
import space.kscience.controls.client.magixFormat
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.subscribe
import space.kscience.magix.rsocket.rSocketWithTcp
import space.kscience.magix.rsocket.rSocketWithWebSockets
import space.kscience.magix.server.RSocketMagixFlowPlugin
import space.kscience.magix.server.startMagixServer
import space.kscience.plotly.*
import space.kscience.plotly.server.PlotlyUpdateMode
import space.kscience.plotly.server.serve
import space.kscience.plotly.server.show
import space.kscince.magix.zmq.ZmqMagixFlowPlugin
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds


class MassDevice(context: Context, meta: Meta) : DeviceBySpec<MassDevice>(MassDevice, context, meta) {
    private val rng = Random(meta["seed"].int ?: 0)

    private val randomValue get() = rng.nextDouble()

    companion object : DeviceSpec<MassDevice>(), Factory<MassDevice> {

        override fun build(context: Context, meta: Meta): MassDevice = MassDevice(context, meta)

        val value by doubleProperty { randomValue }

        override suspend fun MassDevice.onOpen() {
            doRecurring((meta["delay"].int ?: 10).milliseconds) {
                read(value)
            }
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
suspend fun main() {
    val context = Context("Mass")

    context.startMagixServer(
        RSocketMagixFlowPlugin(),
        ZmqMagixFlowPlugin()
    )

    val numDevices = 100

    repeat(numDevices) {
        context.launch(newFixedThreadPoolContext(2, "Device${it}")) {
            delay(1)
            val deviceContext = Context("Device${it}") {
                plugin(DeviceManager)
            }

            val deviceManager = deviceContext.request(DeviceManager)

            deviceManager.install("device$it", MassDevice)

            val endpointId = "device$it"
            val deviceEndpoint = MagixEndpoint.rSocketWithTcp("localhost")
            deviceManager.launchMagixService(deviceEndpoint, endpointId)
        }
    }

    val application = Plotly.serve(port = 9091, scope = context) {
        updateMode = PlotlyUpdateMode.PUSH
        updateInterval = 1000
        page { container ->
            plot(renderer = container, config = PlotlyConfig { saveAsSvg() }) {
                layout {
//                    title = "Latest event"

                    xaxis.title = "Device number"
                    yaxis.title = "Maximum latency in ms"
                }
                bar {
                    launch(Dispatchers.IO) {
                        val monitorEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")

                        val mutex = Mutex()

                        val latest = HashMap<String, Duration>()
                        val max = HashMap<String, Duration>()

                        monitorEndpoint.subscribe(DeviceManager.magixFormat).onEach { (magixMessage, payload) ->
                            mutex.withLock {
                                val delay = Clock.System.now() - payload.time!!
                                latest[magixMessage.sourceEndpoint] = Clock.System.now() - payload.time!!
                                max[magixMessage.sourceEndpoint] =
                                    maxOf(delay, max[magixMessage.sourceEndpoint] ?: ZERO)
                            }
                        }.launchIn(this)

                        while (isActive) {
                            delay(200)
                            mutex.withLock {
                                val sorted = max.mapKeys { it.key.substring(6).toInt() }.toSortedMap()
                                latest.clear()
                                max.clear()
                                x.numbers = sorted.keys
                                y.numbers = sorted.values.map { it.inWholeMicroseconds / 1000.0 + 0.0001 }
                            }
                        }
                    }
                }
            }
        }
    }

    application.show()

    while (readlnOrNull().isNullOrBlank()) {

    }
}
