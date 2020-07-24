package hep.dataforge.control.controllers

import hep.dataforge.control.api.Device
import hep.dataforge.control.api.DeviceListener
import hep.dataforge.control.controllers.DeviceMessage.Companion.PROPERTY_CHANGED_ACTION
import hep.dataforge.io.Envelope
import hep.dataforge.io.Responder
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.wrap
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

    suspend fun respondMessage(
        request: DeviceMessage
    ): DeviceMessage = if (request.target != null && request.target != deviceTarget) {
        DeviceMessage.fail {
            comment = "Wrong target name $deviceTarget expected but ${request.target} found"
        }
    } else try {
        val result: List<MessageData> = when (val action = request.type) {
            GET_PROPERTY_ACTION -> {
                request.data.map { property ->
                    MessageData {
                        name = property.name
                        value = device.getProperty(name)
                    }
                }
            }
            SET_PROPERTY_ACTION -> {
                request.data.map { property ->
                    val propertyName: String = property.name
                    val propertyValue = property.value
                    if (propertyValue == null) {
                        device.invalidateProperty(propertyName)
                    } else {
                        device.setProperty(propertyName, propertyValue)
                    }
                    MessageData {
                        name = propertyName
                        value = device.getProperty(propertyName)
                    }
                }
            }
            EXECUTE_ACTION -> {
                request.data.map { payload ->
                    MessageData {
                        name = payload.name
                        value = device.exec(payload.name, payload.value)
                    }
                }
            }
            PROPERTY_LIST_ACTION -> {
                device.propertyDescriptors.map { descriptor ->
                    MessageData {
                        name = descriptor.name
                        value = MetaItem.NodeItem(descriptor.config)
                    }
                }
            }

            ACTION_LIST_ACTION -> {
                device.actionDescriptors.map { descriptor ->
                    MessageData {
                        name = descriptor.name
                        value = MetaItem.NodeItem(descriptor.config)
                    }
                }
            }

            else -> {
                error("Unrecognized action $action")
            }
        }
        DeviceMessage.ok {
            source = deviceTarget
            target = request.source
            data = result
        }
    } catch (ex: Exception) {
        DeviceMessage.fail {
            comment = ex.message
        }
    }

    override fun consume(message: Envelope) {
        // Fire the respond procedure and forget about the result
        scope.launch {
            respond(message)
        }
    }

    override suspend fun respond(request: Envelope): Envelope {
        val requestMessage = DeviceMessage.wrap(request.meta)
        val responseMessage = respondMessage(requestMessage)
        return SimpleEnvelope(responseMessage.toMeta(), Binary.EMPTY)
    }

    override fun propertyChanged(propertyName: String, value: MetaItem<*>?) {
        if (value == null) return
        scope.launch {
            val change = DeviceMessage.ok {
                this.source = deviceTarget
                type = PROPERTY_CHANGED_ACTION
                property {
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