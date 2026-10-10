package space.kscience.controls.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.dataforge.names.Name
import kotlin.time.Instant

@Serializable
public sealed interface DeviceTreeMessage: DeviceMessage {
    override val time: Instant
    override val targetDevice: Name? get() =  null
}


/**
 * Root device changed or removed
 */
@Serializable
@SerialName("tree.rootDeviceChanged")
public data class DeviceTreeRootDeviceChangedMessage(
    override val time: Instant,
    override val sourceDevice: Name,
    override val comment: String? = null
) : DeviceTreeMessage {
    override fun changeSource(block: (Name) -> Name): DeviceTreeRootDeviceChangedMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * Child device changed or removed
 */
@Serializable
@SerialName("tree.childDeviceChanged")
public data class DeviceTreeChildDeviceChangedMessage(
    override val time: Instant,
    val childDeviceName: String,
    override val sourceDevice: Name,
    override val comment: String? = null
) : DeviceTreeMessage{
    override fun changeSource(block: (Name) -> Name): DeviceMessage  = copy(sourceDevice = block(sourceDevice))
}