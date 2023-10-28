package space.kscience.controls.constructor

import space.kscience.controls.api.Device
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.doubleProperty
import space.kscience.dataforge.meta.transformations.MetaConverter


/**
 * A regulator with target value and current position
 */
public interface Regulator : Device {
    /**
     * Get or set target value
     */
    public var target: Double

    /**
     * Current position value
     */
    public val position: Double

    public companion object : DeviceSpec<Regulator>() {
        public val target: DevicePropertySpec<Regulator, Double> by property(MetaConverter.double, Regulator::target)

        public val position: DevicePropertySpec<Regulator, Double> by doubleProperty { position }
    }
}