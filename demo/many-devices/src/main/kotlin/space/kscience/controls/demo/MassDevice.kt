package space.kscience.controls.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.kscience.controls.client.connectToMagix
import space.kscience.controls.client.controlsMagixFormat
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
import space.kscience.magix.rsocket.rSocketStreamWithTcp
import space.kscience.magix.server.RSocketMagixFlowPlugin
import space.kscience.magix.server.startMagixServer
import space.kscience.plotly.Plotly
import space.kscience.plotly.bar
import space.kscience.plotly.layout
import space.kscience.plotly.plot
import space.kscience.plotly.server.PlotlyUpdateMode
import space.kscience.plotly.server.serve
import space.kscience.plotly.server.show
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds


class MassDevice(context: Context, meta: Meta) : DeviceBySpec<MassDevice>(MassDevice, context, meta) {
    private val rng = Random(meta["seed"].int ?: 0)

    private val randomValue get() = rng.nextDouble()

    companion object : DeviceSpec<MassDevice>(), Factory<MassDevice> {

        override fun build(context: Context, meta: Meta): MassDevice = MassDevice(context, meta)

        val value by doubleProperty { randomValue }

        override suspend fun MassDevice.onOpen() {
            doRecurring(100.milliseconds) {
                read(value)
            }
        }
    }
}

fun main() {
    val context = Context("Mass")

    context.startMagixServer(
        RSocketMagixFlowPlugin(),
//        ZmqMagixFlowPlugin()
    )

    val numDevices = 100

    context.launch(Dispatchers.IO) {
        repeat(numDevices) {
            val deviceContext = Context("Device${it}") {
                plugin(DeviceManager)
            }

            val deviceManager = deviceContext.request(DeviceManager)

            deviceManager.install("device$it", MassDevice)

            val endpointId = "device$it"
            val deviceEndpoint = MagixEndpoint.rSocketStreamWithTcp("localhost")
            deviceManager.connectToMagix(deviceEndpoint, endpointId)
        }
    }

    val application = Plotly.serve(port = 9091, scope = context) {
        updateMode = PlotlyUpdateMode.PUSH
        updateInterval = 1000
        page { container ->
            plot(renderer = container) {
                layout {
                    title = "Latest event"
                }
                bar {
                    launch(Dispatchers.Default){
                        val monitorEndpoint = MagixEndpoint.rSocketStreamWithTcp("localhost")

                        val latest = ConcurrentHashMap<String, Instant>()

                        monitorEndpoint.subscribe(controlsMagixFormat).onEach { (magixMessage, payload) ->
                            latest[magixMessage.origin] = payload.time ?: Clock.System.now()
                        }.launchIn(this)

                        while (isActive) {
                            delay(200)
                            val now = Clock.System.now()
                            val sorted = latest.mapKeys { it.key.substring(6).toInt() }.toSortedMap()
                            x.numbers = sorted.keys
                            y.numbers = sorted.values.map { now.minus(it).inWholeMilliseconds / 1000.0 }
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
