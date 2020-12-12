package hep.dataforge.control.controllers

import hep.dataforge.control.api.Device
import hep.dataforge.control.api.DeviceHub
import hep.dataforge.control.api.get
import hep.dataforge.control.messages.*
import hep.dataforge.meta.DFExperimental
import hep.dataforge.meta.Meta
import hep.dataforge.meta.MetaItem
import hep.dataforge.names.Name
import hep.dataforge.names.toName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The [DeviceController] wraps device operations in [DeviceMessage]
 */
@OptIn(DFExperimental::class)
public class DeviceController(
    public val device: Device,
    public val deviceName: String,
) {

    private val propertyChanges = device.propertyFlow.map { (propertyName: String, value: MetaItem<*>) ->
        PropertyChangedMessage(
            sourceDevice = deviceName,
            key = propertyName,
            value = value,
        )
    }

    /**
     * The flow of outgoing messages
     */
    public val messages: Flow<DeviceMessage> get() = propertyChanges

    public suspend fun respondMessage(message: DeviceMessage): DeviceMessage =
        respondMessage(device, deviceName, message)


    public companion object {
        public const val GET_PROPERTY_ACTION: String = "read"
        public const val SET_PROPERTY_ACTION: String = "write"
        public const val EXECUTE_ACTION: String = "execute"
        public const val PROPERTY_LIST_ACTION: String = "propertyList"
        public const val ACTION_LIST_ACTION: String = "actionList"

//        internal suspend fun respond(device: Device, deviceTarget: String, request: Envelope): Envelope {
//            val target = request.meta["target"].string
//            return try {
//                if (device is Responder) {
//                    device.respond(request)
//                } else if (request.data == null) {
//                    respondMessage(device, deviceTarget, DeviceMessage.fromMeta(request.meta)).toEnvelope()
//                } else if (target != null && target != deviceTarget) {
//                    error("Wrong target name $deviceTarget expected but $target found")
//                } else error("Device does not support binary response")
//            } catch (ex: Exception) {
//                val requestSourceName = request.meta[DeviceMessage.SOURCE_KEY].string
//                DeviceMessage.error(ex, sourceDevice = deviceTarget, targetDevice = requestSourceName).toEnvelope()
//            }
//        }

        internal suspend fun respondMessage(
            device: Device,
            deviceTarget: String,
            request: DeviceMessage,
        ): DeviceMessage = try {
            when (request) {
                is PropertyGetMessage -> {
                    PropertyChangedMessage(
                        key = request.property,
                        value = device.getProperty(request.property),
                        sourceDevice = deviceTarget,
                        targetDevice = request.sourceDevice
                    )
                }
                is PropertySetMessage -> {
                    if (request.value == null) {
                        device.invalidateProperty(request.property)
                    } else {
                        device.setProperty(request.property, request.value)
                    }
                    PropertyChangedMessage(
                        key = request.property,
                        value = device.getProperty(request.property),
                        sourceDevice = deviceTarget,
                        targetDevice = request.sourceDevice
                    )
                }
                is ActionExecuteMessage -> {
                    ActionResultMessage(
                        action = request.action,
                        result = device.execute(request.action, request.argument),
                        sourceDevice = deviceTarget,
                        targetDevice = request.sourceDevice
                    )
                }
                is GetDescriptionMessage -> {
                    val descriptionMeta = Meta {
                        "properties" put {
                            device.propertyDescriptors.map { descriptor ->
                                descriptor.name put descriptor.config
                            }
                        }
                        "actions" put {
                            device.actionDescriptors.map { descriptor ->
                                descriptor.name put descriptor.config
                            }
                        }
                    }

                    DescriptionMessage(
                        description = descriptionMeta,
                        sourceDevice = deviceTarget,
                        targetDevice = request.sourceDevice
                    )
                }

                is DescriptionMessage, is PropertyChangedMessage, is ActionResultMessage, is BinaryNotificationMessage, is DeviceErrorMessage, is EmptyDeviceMessage -> {
                    //Those messages are ignored
                    EmptyDeviceMessage(
                        sourceDevice = deviceTarget,
                        targetDevice = request.sourceDevice,
                        comment = "The message is ignored"
                    )
                }
            }
        } catch (ex: Exception) {
            DeviceMessage.error(ex, sourceDevice = deviceTarget, targetDevice = request.sourceDevice)
        }
    }
}


public suspend fun DeviceHub.respondMessage(request: DeviceMessage): DeviceMessage {
    return try {
        val targetName = request.targetDevice?.toName() ?: Name.EMPTY
        val device = this[targetName] ?: error("The device with name $targetName not found in $this")
        DeviceController.respondMessage(device, targetName.toString(), request)
    } catch (ex: Exception) {
        DeviceMessage.error(ex, sourceDevice = request.targetDevice, targetDevice = request.sourceDevice)
    }
}
