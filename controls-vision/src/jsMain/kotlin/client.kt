package space.kscience.controls.vision

import space.kscience.plotly.PlotlyPlugin
import space.kscience.visionforge.html.runVisionClient
import space.kscience.visionforge.markup.MarkupPlugin

public fun main(): Unit = runVisionClient {
    plugin(PlotlyPlugin)
    plugin(MarkupPlugin)
//    plugin(TableVisionJsPlugin)
}