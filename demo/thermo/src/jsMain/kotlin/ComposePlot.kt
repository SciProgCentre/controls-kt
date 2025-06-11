package center.sciprog.controls.demo.thermo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.jetbrains.compose.web.dom.ElementScope
import org.w3c.dom.Element
import space.kscience.plotly.Plot
import space.kscience.plotly.PlotlyConfig
import space.kscience.plotly.plot

@Composable
public fun ElementScope<Element>.PlotlyPlot(plot: Plot, plotlyConfig: PlotlyConfig = PlotlyConfig.empty()) {
    DisposableEffect(plot) {

        scopeElement.plot(plotlyConfig, plot)

        onDispose {

        }
    }
}