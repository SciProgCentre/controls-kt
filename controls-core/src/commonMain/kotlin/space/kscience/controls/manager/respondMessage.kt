package space.kscience.controls.manager

import space.kscience.controls.api.*
import space.kscience.dataforge.names.Name

/**
 * Process a message targeted at this [Device], assuming its name to be used in responses is [targetDeviceName].
 */
public suspend fun Device.respondMessage(targetDeviceName: Name, request: DeviceMessage): DeviceMessage? = try {
    when (request) {
        is PropertyGetMessage -> {
            PropertyChangedMessage(
                time = clock.now(),
                property = request.property,
                value = getOrReadProperty(request.property),
                sourceDevice = targetDeviceName,
                targetDevice = request.sourceDevice,
            )
        }

        is PropertySetMessage -> {
            writeProperty(request.property, request.value)
            PropertyChangedMessage(
                time = clock.now(),
                property = request.property,
                value = getOrReadProperty(request.property),
                sourceDevice = targetDeviceName,
                targetDevice = request.sourceDevice,
            )
        }

        is ActionExecuteMessage -> {
            ActionResultMessage(
                time = clock.now(),
                action = request.action,
                result = execute(request.action, request.argument),
                requestId = request.requestId,
                sourceDevice = targetDeviceName,
                targetDevice = request.sourceDevice
            )
        }

        is GetDescriptionMessage -> {
            DescriptionMessage(
                time = clock.now(),
                description = meta,
                properties = propertyDescriptors,
                actions = actionDescriptors,
                sourceDevice = targetDeviceName,
                targetDevice = request.sourceDevice
            )
        }

        is DescriptionMessage,
        is PropertyChangedMessage,
        is ActionResultMessage,
        is BinaryNotificationMessage,
        is DeviceErrorMessage,
        is EmptyDeviceMessage,
        is DeviceLogMessage,
        is DeviceLifeCycleMessage,
        is DeviceTreeMessage
            -> null
    }
} catch (ex: Exception) {
    DeviceMessage.error(
        time = clock.now(),
        cause = ex,
        sourceDevice = targetDeviceName,
        targetDevice = request.sourceDevice
    )
}

/**
 * Process incoming [DeviceMessage], using tree naming to find target.
 * If the `targetDevice` is `null`, then the message is sent to each device in this tree
 */
public suspend fun DeviceTree.respondMessage(request: DeviceMessage): List<DeviceMessage> {
    return try {
        val targetName = request.targetDevice
        //broadcast to all devices in this hub
        if (targetName == null) {
            descendantDevices().mapNotNull { (deviceName, device) ->
                device.respondMessage(deviceName, request)
            }
        } else {
            val device = resolveDeviceOrNull(targetName)
            listOfNotNull(device?.respondMessage(targetName, request))
        }
    } catch (ex: Exception) {
        listOf(
            DeviceMessage.error(
                time = request.time, //FIXME add actual time
                cause = ex,
                sourceDevice = Name.EMPTY,
                targetDevice = request.sourceDevice
            )
        )
    }
}
