package space.kscience.controls.demo.constructor

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing
import space.kscience.controls.api.get
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.ClockManager
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.clock
import space.kscience.controls.spec.doRecurring
import space.kscience.controls.spec.name
import space.kscience.controls.spec.write
import space.kscience.controls.vision.plotDeviceProperty
import space.kscience.controls.vision.plotDeviceState
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.visionforge.VisionManager
import space.kscience.visionforge.html.VisionPage
import space.kscience.visionforge.plotly.PlotlyPlugin
import space.kscience.visionforge.plotly.plotly
import space.kscience.visionforge.server.close
import space.kscience.visionforge.server.openInBrowser
import space.kscience.visionforge.server.visionPage
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Suppress("ExtractKtorModule")
public fun main() {
    val context = Context {
        plugin(DeviceManager)
        plugin(PlotlyPlugin)
        plugin(ClockManager)
    }

    val deviceManager = context.request(DeviceManager)
    val visionManager = context.request(VisionManager)

    val state = DoubleRangeState(0.0, -100.0..100.0)

    val pidParameters = PidParameters(
        kp = 2.5,
        ki = 0.0,
        kd = -0.1,
        timeStep = 0.005.seconds
    )

    val device = deviceManager.deviceGroup {
        val drive = virtualDrive("drive", 0.005, state)
        val pid = pid("pid", drive, pidParameters)
        virtualLimitSwitch("start", state.atStartState)
        virtualLimitSwitch("end", state.atEndState)

        val clock = context.clock
        val clockStart = clock.now()

        doRecurring(10.milliseconds) {
            val timeFromStart = clock.now() - clockStart
            val t = timeFromStart.toDouble(DurationUnit.SECONDS)
            val freq = 0.1
            val target = 5 * sin(2.0 * PI * freq * t) +
                    sin(2 * PI * 21 * freq * t + 0.1 * (timeFromStart / pidParameters.timeStep))
            pid.write(Regulator.target, target)
        }
    }

    val server = embeddedServer(CIO, port = 7777) {
        routing {
            staticResources("", null, null)
        }

        visionPage(
            visionManager,
            VisionPage.scriptHeader("js/constructor.js")
        ) {
            vision {
                plotly {
                    plotDeviceState(context, state){
                        name = "value"
                    }
                    plotDeviceProperty(device["pid"], Regulator.target.name){
                        name = "target"
                    }
                }
            }
        }

    }.start(false)

    server.openInBrowser()


    println("Enter 'exit' to close server")
    while (readlnOrNull() != "exit") {
        //
    }

    server.close()
}