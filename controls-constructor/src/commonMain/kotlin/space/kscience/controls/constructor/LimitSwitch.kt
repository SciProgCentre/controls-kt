package center.sciprog.controls.devices.misc

import space.kscience.controls.api.Device
import space.kscience.controls.spec.DeviceBySpec
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.booleanProperty
import space.kscience.dataforge.context.Context


/**
 * A limit switch device
 */
public interface LimitSwitch : Device {

    public val locked: Boolean

    public companion object : DeviceSpec<LimitSwitch>() {
        public val locked: DevicePropertySpec<LimitSwitch, Boolean> by booleanProperty { locked }
    }
}

/**
 * Virtual [LimitSwitch]
 */
public class VirtualLimitSwitch(
    context: Context,
    private val lockedFunction: () -> Boolean,
) : DeviceBySpec<LimitSwitch>(LimitSwitch, context), LimitSwitch {
    override val locked: Boolean get() = lockedFunction()
}