package space.kscience.controls.server

import kotlinx.serialization.Serializable
import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.time.Instant

@Serializable
internal data class PropertySnapshot(
    val descriptor: PropertyDescriptor,
    val value: Meta? = null,
)

@Serializable
internal data class DeviceSnapshotNode(
    val target: Name,
    val meta: Meta,
    val properties: List<PropertySnapshot>,
    val actions: List<ActionDescriptor>,
    val children: List<DeviceSnapshotNode>,
)

@Serializable
internal data class ControlsSnapshot(
    val time: Instant,
    val nodes: List<DeviceSnapshotNode>,
)
