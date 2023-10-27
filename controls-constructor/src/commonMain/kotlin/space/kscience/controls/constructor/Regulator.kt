package center.sciprog.controls.devices.misc

import kotlinx.coroutines.Job
import space.kscience.controls.api.Device
import space.kscience.controls.spec.DeviceBySpec
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.doubleProperty
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.transformations.MetaConverter


/**
 * A single axis drive
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

/**
 * Virtual [Regulator] with speed limit
 */
public class VirtualRegulator(
    context: Context,
    value: Double,
    private val speed: Double,
) : DeviceBySpec<Regulator>(Regulator, context), Regulator {

    private var moveJob: Job? = null

    override var position: Double = value
        private set

    override var target: Double = value


}