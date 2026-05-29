package space.kscience.controls.compose.koala

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.koalaplot.core.xygraph.AxisModel
import io.github.koalaplot.core.xygraph.TickValues
import kotlin.math.floor
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.times

/**
 * A model representing a time-based axis for rendering and computing tick values.
 *
 * @constructor Creates a TimeAxisModel with the specified minimum spacing between major ticks and a function
 * providing the range of time values to display on the axis.
 * @param minimumMajorTickSpacing Minimum distance (in Dp) between two major ticks on the axis.
 * @param rangeProvider A function that returns the current range of time values as a closed range of `Instant`.
 */
public class TimeAxisModel(
    private val minimumMajorTickSpacing: Dp = 50.dp,
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

    override fun offsetToValue(offset: Float): Instant {
        val currentRange = rangeProvider()
        return currentRange.start + (currentRange.endInclusive - currentRange.start) * offset.toDouble()
    }

    public companion object {
        public fun recent(
            duration: Duration,
            clock: Clock = Clock.System,
            minimumMajorTickSpacing: Dp = 50.dp
        ): TimeAxisModel = TimeAxisModel(minimumMajorTickSpacing) {
            val now = clock.now()
            (now - duration)..now
        }
    }
}