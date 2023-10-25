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
public interface Drive : Device {
    /**
     * Get or set target value
     */
    public var target: Double

    /**
     * Current position value
     */
    public val position: Double

    public companion object : DeviceSpec<Drive>() {
        public val target: DevicePropertySpec<Drive, Double> by property(MetaConverter.double, Drive::target)

        public val position: DevicePropertySpec<Drive, Double> by doubleProperty { position }
    }
}

/**
 * Virtual [Drive] with speed limit
 */
public class VirtualDrive(
    context: Context,
    value: Double,
    private val speed: Double,
) : DeviceBySpec<Drive>(Drive, context), Drive {

    private var moveJob: Job? = null

    override var position: Double = value
        private set

    override var target: Double = value


}