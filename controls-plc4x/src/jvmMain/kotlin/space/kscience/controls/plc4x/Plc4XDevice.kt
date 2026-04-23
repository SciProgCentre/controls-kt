package space.kscience.controls.plc4x

import kotlinx.coroutines.future.await
import org.apache.plc4x.java.api.PlcConnection
import org.apache.plc4x.java.api.messages.PlcBrowseItem
import org.apache.plc4x.java.api.messages.PlcTagResponse
import org.apache.plc4x.java.api.messages.PlcWriteRequest
import org.apache.plc4x.java.api.messages.PlcWriteResponse
import org.apache.plc4x.java.api.types.PlcResponseCode
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


/**
 * Send a ping request and suspend until it comes back
 */
public suspend fun PlcConnection.pingPong(): PlcResponseCode = ping().await().responseCode

/**
 * Send a browse request to list available tags
 */
public suspend fun PlcConnection.browse(): Map<String, MutableList<PlcBrowseItem>> {
    require(metadata.isBrowseSupported){"Browse actions are not supported on connection"}
    val request = browseRequestBuilder().build()
    val response = request.execute().await()

    return response.queryNames.associateWith { response.getValues(it) }
}

/**
 * Send read request and suspend until it returns. Throw a [PlcException] if at least one tag read fails.
 *
 * @throws PlcException
 */
public suspend fun PlcConnection.read(plc4xProperty: Plc4xProperty): Meta = with(plc4xProperty) {
    require(metadata.isReadSupported) {"Read actions are not supported on connections"}
    val request = readRequestBuilder().request().build()
    val response = request.execute().await()
    response.throwOnFail()
    response.readProperty()
}


/**
 * Send write request and suspend until it finishes. Throw a [PlcException] if at least one tag write fails.
 *
 * @throws PlcException
 */
public suspend fun PlcConnection.write(plc4xProperty: Plc4xProperty, value: Meta): Unit = with(plc4xProperty) {
    require(metadata.isWriteSupported){"Write actions are not supported on connection"}
    val request: PlcWriteRequest = writeRequestBuilder().writeProperty(value).build()
    val response: PlcWriteResponse = request.execute().await()
    response.throwOnFail()
}