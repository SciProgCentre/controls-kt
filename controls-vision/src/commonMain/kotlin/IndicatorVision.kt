package space.kscience.controls.vision

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.node
import space.kscience.visionforge.AbstractVision
import space.kscience.visionforge.Vision

/**
 * A [Vision] that shows an indicator
 */
public class IndicatorVision: AbstractVision() {
    public val value: Meta? by properties.node()
}

///**
// * A [Vision] that allows both showing the value and changing it
// */
//public interface RegulatorVision: IndicatorVision{
//
//}