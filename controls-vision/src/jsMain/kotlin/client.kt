package space.kscience.controls.vision

import space.kscience.visionforge.markup.MarkupPlugin
import space.kscience.visionforge.plotly.PlotlyPlugin
import space.kscience.visionforge.runVisionClient

public fun main(): Unit = runVisionClient {
    plugin(PlotlyPlugin)
    plugin(MarkupPlugin)
//    plugin(TableVisionJsPlugin)
}