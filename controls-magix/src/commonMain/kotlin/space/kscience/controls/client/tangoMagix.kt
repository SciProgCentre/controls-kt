package space.kscience.controls.client

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import space.kscience.controls.api.getOrReadProperty
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.get
import space.kscience.magix.api.*

public const val TANGO_MAGIX_FORMAT: String = "tango"

/*
  See https://github.com/waltz-controls/rfc/tree/master/4 for details

  "action":"read|write|exec|pipe",
  "timestamp": "int",
  "host":"tango_host",
  "device":"device name",
  "name":"attribute, command or pipe's name",
  "[value]":"attribute's value",
  "[quality]":"VALID|WARNING|ALARM",
  "[argin]":"command argin",
  "[argout]":"command argout",
  "[data]":"pipe's data",
  "[errors]":[]
 */

@Serializable
public enum class TangoAction {
    read,
    write,
    exec,
    pipe
}

@Serializable
public enum class TangoQuality {
    VALID,
    WARNING,
    ALARM
}

@Serializable
public data class TangoPayload(
    val action: TangoAction,
    val timestamp: Int,
    val host: String,
    val device: String,
    val name: String,
    val value: Meta? = null,
    val quality: TangoQuality = TangoQuality.VALID,
    val argin: Meta? = null,
    val argout: Meta? = null,
    val data: Meta? = null,
    val errors: List<String>? = null,
)

internal val tangoMagixFormat = MagixFormat(
    TangoPayload.serializer(),
    setOf("tango")
)

/**
 * Controls-kt device binding for Tango-flavored magix loop
 */
public fun DeviceManager.launchTangoMagix(
    endpoint: MagixEndpoint,
    endpointID: String = TANGO_MAGIX_FORMAT,
): Job {

    suspend fun respond(request: MagixMessage, payload: TangoPayload, payloadBuilder: (TangoPayload) -> TangoPayload) {
        endpoint.send(
            tangoMagixFormat,
            payload = payloadBuilder(payload),
            source = endpointID,
            id = generateId(request),
            parentId = request.id
        )
    }


    return context.launch {
        endpoint.subscribe(tangoMagixFormat).onEach { (request, payload) ->
            try {
                val device = devices[payload.device] ?: error("Device ${payload.device} not found")
                when (payload.action) {
                    TangoAction.read -> {
                        val value = device.getOrReadProperty(payload.name)
                        respond(request, payload) { requestPayload ->
                            requestPayload.copy(
                                value = value,
                                quality = TangoQuality.VALID
                            )
                        }
                    }

                    TangoAction.write -> {
                        payload.value?.let { value ->
                            device.writeProperty(payload.name, value)
                        }
                        //wait for value to be written and return final state
                        val value = device.getOrReadProperty(payload.name)
                        respond(request, payload) { requestPayload ->
                            requestPayload.copy(
                                value = value,
                                quality = TangoQuality.VALID
                            )
                        }
                    }

                    TangoAction.exec -> {
                        val result = device.execute(payload.name, payload.argin)
                        respond(request, payload) { requestPayload ->
                            requestPayload.copy(
                                argout = result,
                                quality = TangoQuality.VALID
                            )
                        }
                    }

                    TangoAction.pipe -> TODO("Pipe not implemented")
                }
            } catch (ex: Exception) {
                logger.error(ex) { "Error while responding to message" }
                endpoint.send(
                    tangoMagixFormat,
                    payload = payload.copy(quality = TangoQuality.WARNING),
                    source = endpointID,
                    id = generateId(request),
                    parentId = request.id
                )
            }
        }.launchIn(this)

//TODO implement subscriptions?
//    controller.messageOutput().onEach { payload ->
//        endpoint.broadcast(
//            MagixMessage(
//                format = TANGO_MAGIX_FORMAT,
//                id = "df[${payload.hashCode()}]",
//                origin = endpointID,
//                payload = payload
//            )
//        )
//    }.catch { error ->
//        logger.error(error) { "Error while sending a message" }
//    }.launchIn(this)
    }
}