import space.kscience.visionforge.plotly.PlotlyPlugin
import space.kscience.visionforge.runVisionClient

public fun main(): Unit = runVisionClient {
    plugin(PlotlyPlugin)
}