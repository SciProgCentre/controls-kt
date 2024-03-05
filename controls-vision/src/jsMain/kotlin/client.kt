package space.kscience.controls.vision

import space.kscience.visionforge.html.runVisionClient
import space.kscience.visionforge.markup.MarkupPlugin
import space.kscience.visionforge.plotly.PlotlyPlugin

public fun main(): Unit = runVisionClient {
    plugin(PlotlyPlugin)
    plugin(MarkupPlugin)
//    plugin(TableVisionJsPlugin)
}