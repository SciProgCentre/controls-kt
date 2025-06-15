package center.sciprog.controls.demo.thermo

import space.kscience.plotly.PlotlyJsPlugin
import space.kscience.visionforge.VisionManager
import space.kscience.visionforge.html.runVisionClient


fun main() = runVisionClient {
    plugin(VisionManager)
    plugin(PlotlyJsPlugin)
    plugin(ThermoSensorPlugin)
}