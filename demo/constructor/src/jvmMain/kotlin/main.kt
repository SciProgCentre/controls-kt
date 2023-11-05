package space.kscience.controls.demo.constructor

import space.kscience.controls.api.get
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.ClockManager
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.clock
import space.kscience.controls.spec.doRecurring
import space.kscience.controls.spec.name
import space.kscience.controls.spec.write
import space.kscience.controls.vision.plot
import space.kscience.controls.vision.plotDeviceProperty
import space.kscience.controls.vision.plotNumberState
import space.kscience.controls.vision.showDashboard
import space.kscience.dataforge.context.Context
import space.kscience.plotly.models.ScatterMode
import space.kscience.visionforge.plotly.PlotlyPlugin
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

    val state = DoubleRangeState(0.0, -5.0..5.0)

    val pidParameters = PidParameters(
        kp = 2.5,
        ki = 0.0,
        kd = -0.1,
        timeStep = 0.005.seconds
    )

    val device = context.deviceGroup {
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


    context.showDashboard {
        plot {
            plotNumberState(context, state) {
                name = "real position"
            }
            plotDeviceProperty(device["pid"], Regulator.position.name) {
                name = "read position"
            }

            plotDeviceProperty(device["pid"], Regulator.target.name) {
                name = "target"
            }
        }

        plot {
//                    plotBooleanState(context, state.atStartState) {
//                        name = "start"
//                    }
//                    plotBooleanState(context, state.atEndState) {
//                        name = "end"
//                    }
            plotDeviceProperty(device["start"], LimitSwitch.locked.name) {
                name = "start measured"
                mode = ScatterMode.markers
            }
            plotDeviceProperty(device["end"], LimitSwitch.locked.name) {
                name = "end measured"
                mode = ScatterMode.markers
            }
        }

    }
}