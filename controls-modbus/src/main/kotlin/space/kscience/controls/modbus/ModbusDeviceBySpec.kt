package space.kscience.controls.modbus

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceHub
import space.kscience.controls.spec.DeviceBySpec
import space.kscience.controls.spec.DeviceSpec
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.NameToken

/**
 * A variant of [DeviceBySpec] that includes Modbus RTU/TCP/UDP client
 */
public open class ModbusDeviceBySpec<D: Device>(
    context: Context,
    spec: DeviceSpec<D>,
    override val unitId: Int,
    override val master: AbstractModbusMaster,
    private val disposeMasterOnClose: Boolean = true,
    meta: Meta = Meta.EMPTY,
) : ModbusDevice, DeviceBySpec<D>(spec, context, meta){
    override suspend fun onStart() {
        master.connect()
    }

    override fun onStop() {
        if(disposeMasterOnClose){
            master.disconnect()
        }
    }
}


public class ModbusHub(
    public val context: Context,
    public val masterBuilder: () -> AbstractModbusMaster,
    public val specs: Map<NameToken, Pair<Int, DeviceSpec<*>>>,
) : DeviceHub, AutoCloseable {

    public val master: AbstractModbusMaster by lazy(masterBuilder)

    override val devices: Map<NameToken, ModbusDevice> by lazy {
        specs.mapValues { (_, pair) ->
            ModbusDeviceBySpec(
                context,
                pair.second,
                pair.first,
                master
            )
        }
    }

    override fun close() {
        master.disconnect()
    }
}
