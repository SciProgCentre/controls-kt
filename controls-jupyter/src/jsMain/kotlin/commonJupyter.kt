import space.kscience.plotly.PlotlyPlugin
import space.kscience.visionforge.html.runVisionClient
import space.kscience.visionforge.jupyter.VFNotebookClient
import space.kscience.visionforge.markup.MarkupPlugin

public fun main(): Unit = runVisionClient {
//    plugin(DeviceManager)
//    plugin(ClockManager)
    plugin(PlotlyPlugin)
    plugin(MarkupPlugin)
//    plugin(TableVisionJsPlugin)
    plugin(VFNotebookClient)
}

