package space.kscience.controls.tagtable

import kotlinx.coroutines.future.await
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import org.apache.plc4x.java.DefaultPlcDriverManager
import org.apache.plc4x.java.api.PlcConnection
import org.apache.plc4x.java.api.types.PlcValueType
import space.kscience.controls.plc4x.Plc4xProperty
import space.kscience.controls.plc4x.throwOnFail
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.required

/**
 * A tag source that reads values from a PLC4X connection.
 */
public class Plc4xTagSource(
    private val config: Plc4xTagSourceConfig,
    private val timeZone: TimeZone
) : TagSource {


    private var client: PlcConnection? = null

    override suspend fun open() {
        client = DefaultPlcDriverManager().getConnection(config.address).apply {
            connect()
        }
    }

    override fun close() {
        client?.close()
        client = null
    }

    override suspend fun readTag(address: Meta): ValueWithTime<Meta>? = client?.let { client ->
        require(client.metadata.isReadSupported) { "Read actions are not supported on connections" }

        val plcAddress = address[ColumnSpec.address] ?: error("PLC address is required")
        val plcValueType = address[ColumnSpec.plcValueType] ?: error("PLC value type is required")
        val name = address[ColumnSpec.name] ?: "@default"

        with(Plc4xProperty(plcAddress, plcValueType, name)) {
            val request = client.readRequestBuilder().request().build()
            val response = request.execute().await()
            response.throwOnFail()

            val time = response.getDateTime(name).toKotlinLocalDateTime().toInstant(timeZone)
            val value = response.readProperty()
            ValueWithTime(value, time)
        }
    }

    override fun validate(column: TagTableColumn) {
        if (!OpcTagSource.ColumnSpec.validate(column.config)) error("Invalid Plc4x address for column $column")
    }

    public object ColumnSpec : MetaSpec() {
        public val address: MetaRef<String> by string {
            required()
        }
        public val plcValueType: MetaRef<PlcValueType> by enum<PlcValueType> {
            required()
        }
        public val name: MetaRef<String> by string()
    }
}