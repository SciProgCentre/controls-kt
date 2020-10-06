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
import hep.dataforge.meta.*
import hep.dataforge.names.Name
import hep.dataforge.names.toName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.io.Binary

@OptIn(DFExperimental::class)
public class DeviceController(
    public val device: Device,
    public val deviceTarget: String,
    public val scope: CoroutineScope = device.scope,
) : Responder, Consumer, DeviceListener {

    init {
        device.registerListener(this, this)
    }

    private val outputChannel = Channel<Envelope>(Channel.CONFLATED)

    public suspend fun respondMessage(message: DeviceMessage): DeviceMessage =
        respondMessage(device, deviceTarget, message)

    override suspend fun respond(request: Envelope): Envelope = respond(device, deviceTarget, request)

    override fun propertyChanged(propertyName: String, value: MetaItem<*>?) {
        if (value == null) return
        scope.launch {
            val change = DeviceMessage.ok {
                this.sourceName = deviceTarget
                this.action = PROPERTY_CHANGED_ACTION
                this.key = propertyName
                this.value = value
            }
            val envelope = SimpleEnvelope(change.toMeta(), Binary.EMPTY)

            outputChannel.send(envelope)
        }
    }

    public fun recieving(): Flow<Envelope> = outputChannel.consumeAsFlow()

    @DFExperimental
    override fun consume(message: Envelope) {
        // Fire the respond procedure and forget about the result
        scope.launch {
            respond(message)
        }
    }

    public companion object {
        public const val GET_PROPERTY_ACTION: String = "read"
        public const val SET_PROPERTY_ACTION: String = "write"
        public const val EXECUTE_ACTION: String = "execute"
        public const val PROPERTY_LIST_ACTION: String = "propertyList"
        public const val ACTION_LIST_ACTION: String = "actionList"

        internal suspend fun respond(device: Device, deviceTarget: String, request: Envelope): Envelope {
            val target = request.meta["target"].string
            return try {
                if (request.data == null) {
                    respondMessage(device, deviceTarget, DeviceMessage.wrap(request.meta)).wrap()
                } else if (target != null && target != deviceTarget) {
                    error("Wrong target name $deviceTarget expected but $target found")
                } else {
                    val response = device.respondWithData(request).apply {
                        meta {
                            "target" put request.meta["source"].string
                            "source" put deviceTarget
                        }
                    }
                    return response.seal()
                }
            } catch (ex: Exception) {
                DeviceMessage.fail(cause = ex).wrap()
            }
        }

        internal suspend fun respondMessage(
            device: Device,
            deviceTarget: String,
            request: DeviceMessage,
        ): DeviceMessage {
            return try {
                DeviceMessage.ok {
                    targetName = request.sourceName
                    sourceName = deviceTarget
                    action ="response.${request.action}"
                    val requestKey = request.key
                    val requestValue = request.value

                    when (val action = request.action) {
                        GET_PROPERTY_ACTION -> {
                            key = requestKey
                            value = device.getProperty(requestKey ?: error("Key field is not defined in request"))
                        }
                        SET_PROPERTY_ACTION -> {
                            require(requestKey != null) { "Key field is not defined in request" }
                            if (requestValue == null) {
                                device.invalidateProperty(requestKey)
                            } else {
                                device.setProperty(requestKey, requestValue)
                            }
                            key = requestKey
                            value = device.getProperty(requestKey)
                        }
                        EXECUTE_ACTION -> {
                            require(requestKey != null) { "Key field is not defined in request" }
                            key = requestKey
                            value = device.execute(requestKey, requestValue)

                        }
                        PROPERTY_LIST_ACTION -> {
                            value = Meta {
                                device.propertyDescriptors.map { descriptor ->
                                    descriptor.name put descriptor.config
                                }
                            }.asMetaItem()
                        }
                        ACTION_LIST_ACTION -> {
                            value = Meta {
                                device.actionDescriptors.map { descriptor ->
                                    descriptor.name put descriptor.config
                                }
                            }.asMetaItem()
                        }
                        else -> {
                            error("Unrecognized action $action")
                        }
                    }
                }
            } catch (ex: Exception) {
                DeviceMessage.fail(request, cause = ex)
            }
        }
    }
}


public suspend fun DeviceHub.respondMessage(request: DeviceMessage): DeviceMessage {
    return try {
        val targetName = request.targetName?.toName() ?: Name.EMPTY
        val device = this[targetName] ?: error("The device with name $targetName not found in $this")
        DeviceController.respondMessage(device, targetName.toString(), request)
    } catch (ex: Exception) {
        DeviceMessage.fail(request, cause = ex)
    }
}
