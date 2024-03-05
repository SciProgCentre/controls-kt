package space.kscience.controls.jupyter

import org.jetbrains.kotlinx.jupyter.api.declare
import org.jetbrains.kotlinx.jupyter.api.libraries.resources
import space.kscience.controls.manager.ClockManager
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.plotly.Plot
import space.kscience.visionforge.jupyter.VisionForge
import space.kscience.visionforge.jupyter.VisionForgeIntegration
import space.kscience.visionforge.markup.MarkupPlugin
import space.kscience.visionforge.plotly.PlotlyPlugin
import space.kscience.visionforge.plotly.asVision
import space.kscience.visionforge.visionManager


@OptIn(DFExperimental::class)
public class ControlsJupyter : VisionForgeIntegration(CONTEXT.visionManager) {

    override fun Builder.afterLoaded(vf: VisionForge) {

        resources {
            js("controls-jupyter") {
                classPath("js/controls-jupyter.js")
            }
        }

        onLoaded {
            declare("context" to CONTEXT)
        }

        import(
            "kotlin.time.*",
            "kotlin.time.Duration.Companion.milliseconds",
            "kotlin.time.Duration.Companion.seconds",
//            "space.kscience.tables.*",
            "space.kscience.dataforge.meta.*",
            "space.kscience.dataforge.context.*",
            "space.kscience.plotly.*",
            "space.kscience.plotly.models.*",
            "space.kscience.visionforge.plotly.*",
            "space.kscience.controls.manager.*",
            "space.kscience.controls.constructor.*",
            "space.kscience.controls.vision.*",
            "space.kscience.controls.spec.*"
        )

//        render<Table<*>> { table ->
//            vf.produceHtml {
//                vision { table.toVision() }
//            }
//        }

        render<Plot> { plot ->
            vf.produceHtml {
                vision { plot.asVision() }
            }
        }
    }

    public companion object {
        private val CONTEXT: Context = Context("controls-jupyter") {
            plugin(DeviceManager)
            plugin(ClockManager)
            plugin(PlotlyPlugin)
//            plugin(TableVisionPlugin)
            plugin(MarkupPlugin)
        }
    }
}
