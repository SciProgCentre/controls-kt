package space.kscience.controls.vision

import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import space.kscience.dataforge.context.Context
import space.kscience.plotly.PlotlyPlugin
import space.kscience.visionforge.html.HtmlVisionFragment
import space.kscience.visionforge.html.VisionPage
import space.kscience.visionforge.markup.MarkupPlugin
import space.kscience.visionforge.server.VisionRoute
import space.kscience.visionforge.server.openInBrowser
import space.kscience.visionforge.server.visionPage
import space.kscience.visionforge.visionManager

public suspend fun Context.showDashboard(
    port: Int = 7080,
    routes: Routing.() -> Unit = {},
    configurationBuilder: VisionRoute.() -> Unit = {},
    visionFragment: HtmlVisionFragment,
): EmbeddedServer<*, *> {
    //create a sub-context for visualization
    val visualisationContext = buildContext {
        plugin(PlotlyPlugin)
        plugin(ControlVisionPlugin)
        plugin(MarkupPlugin)
    }

    return visualisationContext.embeddedServer(CIO, port = port) {
        routing {
            staticResources("js", "js", null)
            staticResources("css", "css", null)
            routes()
        }

        visionPage(
            visualisationContext.visionManager,
            VisionPage.scriptHeader("js/controls-vision.js"),
            routeConfiguration = configurationBuilder,
            visionFragment = visionFragment
        )
    }.also {
        it.start(false)
        it.openInBrowser()


        println("Enter 'exit' to close server")
        while (readlnOrNull() != "exit") {
            //
        }

        it.stop()
    }
}

//context(consumer: VisionTagConsumer<*>)
//public fun TagConsumer<*>.plot(
//    config: PlotlyConfig = PlotlyConfig(),
//    block: Plot.() -> Unit,
//) {
//    vision {
//        plotly(config, block)
//    }
//}
