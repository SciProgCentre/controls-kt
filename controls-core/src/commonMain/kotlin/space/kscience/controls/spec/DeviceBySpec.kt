package space.kscience.controls.spec

import space.kscience.controls.api.Device
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.meta.Meta

/**
 * A device generated from specification
 * @param D recursive self-type for properties and actions
 */
public open class DeviceBySpec<D : Device>(
    public val spec: DeviceSpec<in D>,
    context: Context = Global,
    meta: Meta = Meta.EMPTY,
) : DeviceBase<D>(context, meta) {
    override val properties: Map<String, DevicePropertySpec<D, *>> get() = spec.properties
    override val actions: Map<String, DeviceActionSpec<D, *, *>> get() = spec.actions

    override suspend fun open(): Unit = with(spec) {
        super.open()
        self.onOpen()
    }

    override fun close(): Unit = with(spec) {
        self.onClose()
        super.close()
    }
}