package space.kscience.controls.spec

import space.kscience.controls.api.Device
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta

/**
 * A device generated from specification
 * @param D recursive self-type for properties and actions
 */
public open class DeviceBySpec<D : Device>(
    public val spec: DeviceSpec<in D>,
    context: Context,
    meta: Meta = Meta.EMPTY,
) : DeviceBase<D>(context, meta) {
    override val properties: Map<String, DevicePropertySpec<D, *>> get() = spec.properties
    override val actions: Map<String, DeviceActionSpec<D, *, *>> get() = spec.actions

    override suspend fun onStart(): Unit = with(spec) {
        self.onOpen()
    }

    override suspend fun onStop(): Unit = with(spec){
        self.onClose()
    }


    override fun toString(): String = "Device(spec=$spec)"
}