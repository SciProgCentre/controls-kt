package space.kscience.controls.constructor

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import space.kscience.controls.api.Device
import space.kscience.controls.spec.DeviceBySpec
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.booleanProperty
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.parseAsName


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
    public val lockedState: DeviceState<Boolean>,
) : DeviceBySpec<LimitSwitch>(LimitSwitch, context), LimitSwitch {

    init {
        lockedState.valueFlow.onEach {
            propertyChanged(LimitSwitch.locked, it)
        }.launchIn(this)
    }

    override val locked: Boolean get() = lockedState.value
}

public fun DeviceGroup.virtualLimitSwitch(name: String, lockedState: DeviceState<Boolean>): VirtualLimitSwitch =
    device(name.parseAsName(), VirtualLimitSwitch(context, lockedState))