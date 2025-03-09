package space.kscience.controls.plc4x

import kotlinx.coroutines.future.await
import org.apache.plc4x.java.api.PlcConnection
import org.apache.plc4x.java.api.messages.PlcBrowseItem
import org.apache.plc4x.java.api.messages.PlcTagResponse
import org.apache.plc4x.java.api.messages.PlcWriteRequest
import org.apache.plc4x.java.api.messages.PlcWriteResponse
import org.apache.plc4x.java.api.types.PlcResponseCode
import space.kscience.controls.api.Device
import space.kscience.dataforge.meta.Meta

private val PlcTagResponse.responseCodes: Map<String, PlcResponseCode>
    get() = tagNames.associateWith { getResponseCode(it) }

private val Map<String, PlcResponseCode>.isOK get() = values.all { it == PlcResponseCode.OK }

public class PlcException(public val codes: Map<String, PlcResponseCode>) : Exception() {
    override val message: String
        get() = "Plc request unsuccessful:" + codes.entries.joinToString(prefix = "\n\t", separator = "\n\t") {
            "${it.key}: ${it.value.name}"
        }
}

private fun PlcTagResponse.throwOnFail() {
    val codes = responseCodes
    if (!codes.isOK) throw PlcException(codes)
}


public interface Plc4XDevice : Device {
    public val connection: PlcConnection
}


/**
 * Send ping request and suspend until it comes back
 */
public suspend fun Plc4XDevice.ping(): PlcResponseCode = connection.ping().await().responseCode

/**
 * Send browse request to list available tags
 */
public suspend fun Plc4XDevice.browse(): Map<String, MutableList<PlcBrowseItem>> {
    require(connection.metadata.isBrowseSupported){"Browse actions are not supported on connection"}
    val request = connection.browseRequestBuilder().build()
    val response = request.execute().await()

    return response.queryNames.associateWith { response.getValues(it) }
}

/**
 * Send read request and suspend until it returns. Throw a [PlcException] if at least one tag read fails.
 *
 * @throws PlcException
 */
public suspend fun Plc4XDevice.read(plc4xProperty: Plc4xProperty): Meta = with(plc4xProperty) {
    require(connection.metadata.isReadSupported) {"Read actions are not supported on connections"}
    val request = connection.readRequestBuilder().request().build()
    val response = request.execute().await()
    response.throwOnFail()
    response.readProperty()
}


/**
 * Send write request and suspend until it finishes. Throw a [PlcException] if at least one tag write fails.
 *
 * @throws PlcException
 */
public suspend fun Plc4XDevice.write(plc4xProperty: Plc4xProperty, value: Meta): Unit = with(plc4xProperty) {
    require(connection.metadata.isWriteSupported){"Write actions are not supported on connection"}
    val request: PlcWriteRequest = connection.writeRequestBuilder().writeProperty(value).build()
    val response: PlcWriteResponse = request.execute().await()
    response.throwOnFail()
}