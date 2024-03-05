package space.kscience.controls.manager

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import space.kscience.controls.api.*
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.plus

/**
 * Process a message targeted at this [Device], assuming its name is [deviceTarget].
 */
public suspend fun Device.respondMessage(deviceTarget: Name, request: DeviceMessage): DeviceMessage? = try {
    when (request) {
        is PropertyGetMessage -> {
            PropertyChangedMessage(
                property = request.property,
                value = getOrReadProperty(request.property),
                sourceDevice = deviceTarget,
                targetDevice = request.sourceDevice
            )
        }

        is PropertySetMessage -> {
            writeProperty(request.property, request.value)
            PropertyChangedMessage(
                property = request.property,
                value = getOrReadProperty(request.property),
                sourceDevice = deviceTarget,
                targetDevice = request.sourceDevice
            )
        }

        is ActionExecuteMessage -> {
            ActionResultMessage(
                action = request.action,
                result = execute(request.action, request.argument),
                requestId = request.requestId,
                sourceDevice = deviceTarget,
                targetDevice = request.sourceDevice
            )
        }

        is GetDescriptionMessage -> {
            DescriptionMessage(
                description = meta,
                properties = propertyDescriptors,
                actions = actionDescriptors,
                sourceDevice = deviceTarget,
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
    DeviceMessage.error(ex, sourceDevice = deviceTarget, targetDevice = request.sourceDevice)
}

/**
 * Process incoming [DeviceMessage], using hub naming to find target.
 * If the `targetDevice` is `null`, then message is sent to each device in this hub
 */
public suspend fun DeviceHub.respondHubMessage(request: DeviceMessage): List<DeviceMessage> {
    return try {
        val targetName = request.targetDevice
        if (targetName == null) {
            buildDeviceTree().mapNotNull {
                it.value.respondMessage(it.key, request)
            }
        } else {
            val device = getOrNull(targetName) ?: error("The device with name $targetName not found in $this")
            listOfNotNull(device.respondMessage(targetName, request))
        }
    } catch (ex: Exception) {
        listOf(DeviceMessage.error(ex, sourceDevice = Name.EMPTY, targetDevice = request.sourceDevice))
    }
}

/**
 * Collect all messages from given [DeviceHub], applying proper relative names.
 */
public fun DeviceHub.hubMessageFlow(): Flow<DeviceMessage> {

    val deviceMessageFlow = if (this is Device) messageFlow else emptyFlow()

    val childrenFlows = devices.map { (token, childDevice) ->
        if (childDevice is DeviceHub) {
            childDevice.hubMessageFlow()
        } else {
            childDevice.messageFlow
        }.map { deviceMessage ->
            deviceMessage.changeSource { token + it }
        }
    }

    return merge(deviceMessageFlow, *childrenFlows.toTypedArray())
}