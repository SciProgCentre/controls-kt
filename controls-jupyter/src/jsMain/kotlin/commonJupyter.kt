import space.kscience.visionforge.jupyter.VFNotebookClient
import space.kscience.visionforge.markup.MarkupPlugin
import space.kscience.visionforge.plotly.PlotlyPlugin
import space.kscience.visionforge.runVisionClient

public fun main(): Unit = runVisionClient {
//    plugin(DeviceManager)
//    plugin(ClockManager)
    plugin(PlotlyPlugin)
    plugin(MarkupPlugin)
//    plugin(TableVisionJsPlugin)
    plugin(VFNotebookClient)
}

