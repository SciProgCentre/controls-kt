package ru.mipt.npm.controls.client

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.mipt.npm.controls.api.get
import ru.mipt.npm.controls.api.getOrReadItem
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta

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
    val errors: List<String>? = null
)

public fun DeviceManager.launchTangoMagix(
    endpoint: MagixEndpoint<TangoPayload>,
    endpointID: String = TANGO_MAGIX_FORMAT,
): Job {
    suspend fun respond(request: MagixMessage<TangoPayload>, payloadBuilder: (TangoPayload) -> TangoPayload) {
        endpoint.broadcast(
            request.copy(
                id = generateId(request),
                parentId = request.id,
                origin = endpointID,
                payload = payloadBuilder(request.payload)
            )
        )
    }


    return context.launch {
        endpoint.subscribe().onEach { request ->
            try {
                val device = get(request.payload.device)
                when (request.payload.action) {
                    TangoAction.read -> {
                        val value = device.getOrReadItem(request.payload.name)
                        respond(request) { requestPayload ->
                            requestPayload.copy(
                                value = value,
                                quality = TangoQuality.VALID
                            )
                        }
                    }
                    TangoAction.write -> {
                        request.payload.value?.let { value ->
                            device.writeItem(request.payload.name, value)
                        }
                        //wait for value to be written and return final state
                        val value = device.getOrReadItem(request.payload.name)
                        respond(request) { requestPayload ->
                            requestPayload.copy(
                                value = value,
                                quality = TangoQuality.VALID
                            )
                        }
                    }
                    TangoAction.exec -> {
                        val result = device.execute(request.payload.name, request.payload.argin)
                        respond(request) { requestPayload ->
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
                endpoint.broadcast(
                    request.copy(
                        id = generateId(request),
                        parentId = request.id,
                        origin = endpointID,
                        payload = request.payload.copy(quality = TangoQuality.WARNING)
                    )
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