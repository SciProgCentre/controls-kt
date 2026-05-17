package space.kscience.controls.manager

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import space.kscience.controls.api.*
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.names.plus

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
 * Process incoming [DeviceMessage], using hub naming to find target.
 * If the `targetDevice` is `null`, then the message is sent to each device in this hub
 */
public suspend fun DeviceTree.respondMessage(request: DeviceMessage): List<DeviceMessage> {
    return try {
        val targetName = request.targetDevice
        //broadcast to all devices in this hub
        if (targetName == null) {
            descendantDevices().mapNotNull {(deviceName, device)->
                device.respondMessage(deviceName, request)
            }
        } else {
            val device = resolveDevice(targetName)
            listOfNotNull(device.respondMessage(targetName, request))
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

/**
 * Collect all messages from given [DeviceTree], applying proper relative names.
 */
public fun DeviceTree.messageFlow(): Flow<DeviceMessage> {

    val deviceMessageFlow = device?.messageFlow ?: emptyFlow()

    val childrenFlows = children.map { (deviceName, childDevice) ->
        childDevice.messageFlow().map { deviceMessage ->
            deviceMessage.changeSource { NameToken(deviceName) + it }
        }
    }

    return merge(deviceMessageFlow, *childrenFlows.toTypedArray())
}