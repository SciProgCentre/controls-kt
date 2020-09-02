package hep.dataforge.control.controllers

import hep.dataforge.control.api.Device
import hep.dataforge.control.api.DeviceHub
import hep.dataforge.control.api.DeviceListener
import hep.dataforge.control.api.get
import hep.dataforge.control.controllers.DeviceMessage.Companion.PROPERTY_CHANGED_ACTION
import hep.dataforge.io.Consumer
import hep.dataforge.io.Envelope
import hep.dataforge.io.Responder
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.get
import hep.dataforge.meta.string
import hep.dataforge.meta.wrap
import hep.dataforge.names.Name
import hep.dataforge.names.toName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.io.Binary

class DeviceController(
    val device: Device,
    val deviceTarget: String,
    val scope: CoroutineScope = device.scope
) : Responder, Consumer, DeviceListener {

    init {
        device.registerListener(this, this)
    }

    private val outputChannel = Channel<Envelope>(Channel.CONFLATED)

    suspend fun respondMessage(message: DeviceMessage): DeviceMessage = respondMessage(device, deviceTarget, message)

    override suspend fun respond(request: Envelope): Envelope = respond(device, deviceTarget, request)

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

    override fun consume(message: Envelope) {
        // Fire the respond procedure and forget about the result
        scope.launch {
            respond(message)
        }
    }

    companion object {
        const val GET_PROPERTY_ACTION = "read"
        const val SET_PROPERTY_ACTION = "write"
        const val EXECUTE_ACTION = "execute"
        const val PROPERTY_LIST_ACTION = "propertyList"
        const val ACTION_LIST_ACTION = "actionList"

        internal suspend fun respond(device: Device, deviceTarget: String, request: Envelope): Envelope {
            val target = request.meta["target"].string
            return try {
                if (request.data == null) {
                    respondMessage(device, deviceTarget, DeviceMessage.wrap(request.meta)).wrap()
                } else if (target != null && target != deviceTarget) {
                    error("Wrong target name $deviceTarget expected but $target found")
                } else {
                    val response = device.respond(request).apply {
                        meta {
                            "target" put request.meta["source"].string
                            "source" put deviceTarget
                        }
                    }
                    return response.seal()
                }
            } catch (ex: Exception) {
                DeviceMessage.fail {
                    comment = ex.message
                }.wrap()
            }
        }

        internal suspend fun respondMessage(
            device: Device,
            deviceTarget: String,
            request: DeviceMessage
        ): DeviceMessage {
            return try {
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
                                value = device.execute(payload.name, payload.value)
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
                    target = request.source
                    source = deviceTarget
                    data = result
                }
            } catch (ex: Exception) {
                DeviceMessage.fail {
                    comment = ex.message
                }
            }
        }
    }
}


suspend fun DeviceHub.respondMessage(request: DeviceMessage): DeviceMessage {
    return try {
        val targetName = request.target?.toName() ?: Name.EMPTY
        val device = this[targetName] ?: error("The device with name $targetName not found in $this")
        DeviceController.respondMessage(device, targetName.toString(), request)
    } catch (ex: Exception) {
        DeviceMessage.fail {
            comment = ex.message
        }
    }
}
