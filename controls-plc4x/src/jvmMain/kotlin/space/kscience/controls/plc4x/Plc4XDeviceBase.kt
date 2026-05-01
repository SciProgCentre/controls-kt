package space.kscience.controls.plc4x

import org.apache.plc4x.java.api.PlcConnection
import org.apache.plc4x.java.api.PlcDriverManager
import space.kscience.controls.spec.DeviceBase
import space.kscience.controls.spec.DeviceFactory
import space.kscience.dataforge.meta.Scheme
import space.kscience.dataforge.meta.SchemeSpec
import space.kscience.dataforge.meta.string

public class Plc4XConfig : Scheme() {

    public var endpointUrl: String by string { error("Endpoint url is not defined") }

    public companion object : SchemeSpec<Plc4XConfig>(::Plc4XConfig)
}


public abstract class Plc4XDeviceFactory : DeviceFactory<PlcConnection>() {

    context(base: DeviceBase)
    override suspend fun createState(): PlcConnection {
        val config = Plc4XConfig.read(base.meta)
        return PlcDriverManager.getDefault().connectionManager.getConnection(config.endpointUrl)
    }

    context(base: DeviceBase)
    override suspend fun destroyState(state: PlcConnection) {
        state.close()
    }
}