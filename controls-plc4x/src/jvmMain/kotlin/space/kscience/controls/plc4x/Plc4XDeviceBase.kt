package space.kscience.controls.plc4x

import org.apache.plc4x.java.api.PlcConnection
import space.kscience.controls.spec.DeviceActionSpec
import space.kscience.controls.spec.DeviceBase
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta

public class Plc4XDeviceBase(
    context: Context,
    meta: Meta,
    override val connection: PlcConnection,
) : Plc4XDevice, DeviceBase<Plc4XDevice>(context, meta) {
    override val properties: Map<String, DevicePropertySpec<Plc4XDevice, *>>
        get() = TODO("Not yet implemented")
    override val actions: Map<String, DeviceActionSpec<Plc4XDevice, *, *>> = emptyMap()

    override fun toString(): String {
        TODO("Not yet implemented")
    }
}