package hep.dataforge.control.controllers

import hep.dataforge.control.api.Device
import hep.dataforge.control.api.DeviceListener
import hep.dataforge.control.api.respondMessage
import hep.dataforge.control.controllers.DeviceMessage.Companion.PROPERTY_CHANGED_ACTION
import hep.dataforge.io.Envelope
import hep.dataforge.io.Responder
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.io.Binary

/**
 * A consumer of envelopes
 */
interface Consumer {
    fun consume(message: Envelope): Unit
}

class MessageController(
    val device: Device,
    val deviceTarget: String,
    val scope: CoroutineScope = device.scope
) : Consumer, Responder, DeviceListener {

    init {
        device.registerListener(this, this)
    }

    private val outputChannel = Channel<Envelope>(Channel.CONFLATED)

    override fun consume(message: Envelope) {
        // Fire the respond procedure and forget about the result
        scope.launch {
            respond(message)
        }
    }

    suspend fun respondMessage(message: DeviceMessage): DeviceMessage {
        return try {
            device.respondMessage(message).apply {
                target = message.source
                source = deviceTarget
            }
        } catch (ex: Exception) {
            DeviceMessage.fail {
                comment = ex.message
            }
        }
    }

    override suspend fun respond(request: Envelope): Envelope {
        val target = request.meta["target"].string
        return try {
            if (request.data == null) {
                respondMessage(DeviceMessage.wrap(request.meta)).wrap()
            }else if(target != null && target != deviceTarget) {
                error("Wrong target name $deviceTarget expected but ${target} found")
            } else {
                val response = device.respond(request)
                return SimpleEnvelope(response.meta.edit {
                    "target" put request.meta["source"].string
                    "source" put deviceTarget
                }, response.data)
            }
        } catch (ex: Exception) {
            DeviceMessage.fail {
                comment = ex.message
            }.wrap()
        }
    }

    override fun propertyChanged(propertyName: String, value: MetaItem<*>?) {
        if (value == null) return
        scope.launch {
            val change = DeviceMessage.ok {
                this.source = deviceTarget
                type = PROPERTY_CHANGED_ACTION
                data {
                    name = propertyName
                    this.value = value
                }
            }
            val envelope = SimpleEnvelope(change.toMeta(), Binary.EMPTY)

            outputChannel.send(envelope)
        }
    }

    fun output() = outputChannel.consumeAsFlow()


    companion object {
        const val GET_PROPERTY_ACTION = "read"
        const val SET_PROPERTY_ACTION = "write"
        const val EXECUTE_ACTION = "execute"
        const val PROPERTY_LIST_ACTION = "propertyList"
        const val ACTION_LIST_ACTION = "actionList"
    }
}