package space.kscience.controls.vision

import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import kotlinx.html.TagConsumer
import space.kscience.dataforge.context.Context
import space.kscience.plotly.Plot
import space.kscience.plotly.PlotlyConfig
import space.kscience.visionforge.html.HtmlVisionFragment
import space.kscience.visionforge.html.VisionPage
import space.kscience.visionforge.html.VisionTagConsumer
import space.kscience.visionforge.plotly.plotly
import space.kscience.visionforge.server.VisionRoute
import space.kscience.visionforge.server.close
import space.kscience.visionforge.server.openInBrowser
import space.kscience.visionforge.server.visionPage
import space.kscience.visionforge.visionManager

public fun Context.showDashboard(
    port: Int = 7777,
    routes: Routing.() -> Unit = {},
    configurationBuilder: VisionRoute.() -> Unit = {},
    visionFragment: HtmlVisionFragment,
): ApplicationEngine = embeddedServer(CIO, port = port) {
    routing {
        staticResources("", null, null)
        routes()
    }

    visionPage(
        visionManager,
        VisionPage.scriptHeader("js/controls-vision.js"),
        configurationBuilder = configurationBuilder,
        visionFragment = visionFragment
    )
}.also {
    it.start(false)
    it.openInBrowser()


    println("Enter 'exit' to close server")
    while (readlnOrNull() != "exit") {
        //
    }

    it.close()
}

context(VisionTagConsumer<*>)
public fun TagConsumer<*>.plot(
    config: PlotlyConfig = PlotlyConfig(),
    block: Plot.() -> Unit,
) {
    vision {
        plotly(config, block)
    }
}
