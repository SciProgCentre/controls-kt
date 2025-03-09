package space.kscience.controls.compose

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.koalaplot.core.xygraph.AxisModel
import io.github.koalaplot.core.xygraph.TickValues
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.times

public class TimeAxisModel(
    override val minimumMajorTickSpacing: Dp = 50.dp,
    private val rangeProvider: () -> ClosedRange<Instant>,
) : AxisModel<Instant> {

    override fun computeTickValues(axisLength: Dp): TickValues<Instant> {
        val currentRange = rangeProvider()
        val rangeLength = currentRange.endInclusive - currentRange.start
        val numTicks = floor(axisLength / minimumMajorTickSpacing).toInt()
        return object : TickValues<Instant> {
            override val majorTickValues: List<Instant> = List(numTicks) {
                currentRange.start + it.toDouble() / (numTicks - 1) * rangeLength
            }

            override val minorTickValues: List<Instant> = emptyList()
        }
    }

    override fun computeOffset(point: Instant): Float {
        val currentRange = rangeProvider()
        return ((point - currentRange.start) / (currentRange.endInclusive - currentRange.start)).toFloat()
    }

    public companion object {
        public fun recent(duration: Duration, clock: Clock = Clock.System): TimeAxisModel = TimeAxisModel {
            val now = clock.now()
            (now - duration)..now
        }
    }
}