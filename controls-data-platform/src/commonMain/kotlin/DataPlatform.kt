package space.kscience.controls.timeseries

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import kotlinx.datetime.TimeZone
import org.apache.plc4x.java.api.PlcConnection
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import space.kscience.dataforge.names.Name
import kotlin.time.Clock

public interface DataPlatform: AutoCloseable{

    public suspend fun resolveOpcClient(name: Name): OpcUaClient?

    public suspend fun resolvePlcClient(name: Name): PlcConnection?

    public suspend fun resolveModbusClient(name: Name): AbstractModbusMaster?

    public val clock: Clock get() = Clock.System

    public val timeZone: TimeZone get() = TimeZone.currentSystemDefault()
}