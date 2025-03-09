package space.kscience.controls.plc4x

import org.apache.plc4x.java.api.messages.PlcReadRequest
import org.apache.plc4x.java.api.messages.PlcReadResponse
import org.apache.plc4x.java.api.messages.PlcWriteRequest
import org.apache.plc4x.java.api.types.PlcValueType
import space.kscience.dataforge.meta.Meta

public interface Plc4xProperty {

    public val keys: Set<String>

    public fun PlcReadRequest.Builder.request(): PlcReadRequest.Builder

    public fun PlcReadResponse.readProperty(): Meta

    public fun PlcWriteRequest.Builder.writeProperty(meta: Meta): PlcWriteRequest.Builder
}

private class DefaultPlc4xProperty(
    private val address: String,
    private val plcValueType: PlcValueType,
    private val name: String = "@default",
) : Plc4xProperty {

    override val keys: Set<String> = setOf(name)

    override fun PlcReadRequest.Builder.request(): PlcReadRequest.Builder =
        addTagAddress(name, address)

    override fun PlcReadResponse.readProperty(): Meta =
        getPlcValue(name).toMeta()

    override fun PlcWriteRequest.Builder.writeProperty(meta: Meta): PlcWriteRequest.Builder =
        addTagAddress(name, address, meta.toPlcValue(plcValueType))
}

public fun Plc4xProperty(address: String, plcValueType: PlcValueType, name: String = "@default"): Plc4xProperty =
    DefaultPlc4xProperty(address, plcValueType, name)