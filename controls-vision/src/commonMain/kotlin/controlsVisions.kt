package space.kscience.controls.vision

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.controls.misc.doubleRange
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.convertable
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.string
import space.kscience.visionforge.AbstractControlVision
import space.kscience.visionforge.AbstractVision
import space.kscience.visionforge.Vision

/**
 * A [Vision] that shows a colored indicator
 */
@Serializable
@SerialName("controls.indicator")
public class IndicatorVision : AbstractVision() {
    public val color: String? by properties.string()
}

@Serializable
@SerialName("controls.slider")
public class SliderVision : AbstractControlVision() {
    public var position: Double? by properties.double()
    public var range: ClosedFloatingPointRange<Double>? by properties.convertable(MetaConverter.doubleRange)
}

///**
// * A [Vision] that allows both showing the value and changing it
// */
//public interface RegulatorVision: IndicatorVision{
//
//}