package ru.mipt.npm.controls.controllers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.mipt.npm.controls.api.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.plus

public suspend fun Device.respondMessage(deviceTarget: Name, request: DeviceMessage): DeviceMessage? = try {
    when (request) {
        is PropertyGetMessage -> {
            PropertyChangedMessage(
                property = request.property,
                value = getOrReadItem(request.property),
                sourceDevice = deviceTarget,
                targetDevice = request.sourceDevice
            )
        }

        is PropertySetMessage -> {
            if (request.value == null) {
                invalidate(request.property)
            } else {
                writeItem(request.property, request.value)
            }
            PropertyChangedMessage(
                property = request.property,
                value = getOrReadItem(request.property),
                sourceDevice = deviceTarget,
                targetDevice = request.sourceDevice
            )
        }

        is ActionExecuteMessage -> {
            ActionResultMessage(
                action = request.action,
                result = execute(request.action, request.argument),
                sourceDevice = deviceTarget,
                targetDevice = request.sourceDevice
            )
        }

        is GetDescriptionMessage -> {
            val descriptionMeta = Meta {
                "properties" put {
                    propertyDescriptors.map { descriptor ->
                        descriptor.name put descriptor.toMeta()
                    }
                }
                "actions" put {
                    actionDescriptors.map { descriptor ->
                        descriptor.name put descriptor.toMeta()
                    }
                }
            }

            DescriptionMessage(
                description = descriptionMeta,
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
        -> null
    }
} catch (ex: Exception) {
    DeviceMessage.error(ex, sourceDevice = deviceTarget, targetDevice = request.sourceDevice)
}

public suspend fun DeviceHub.respondHubMessage(request: DeviceMessage): DeviceMessage? {
    return try {
        val targetName = request.targetDevice ?: return null
        val device = getOrNull(targetName) ?: error("The device with name $targetName not found in $this")
        device.respondMessage(targetName, request)
    } catch (ex: Exception) {
        DeviceMessage.error(ex, sourceDevice = Name.EMPTY, targetDevice = request.sourceDevice)
    }
}

/**
 * Collect all messages from given [DeviceHub], applying proper relative names
 */
public fun DeviceHub.hubMessageFlow(scope: CoroutineScope): Flow<DeviceMessage> {
    val outbox = MutableSharedFlow<DeviceMessage>()
    if (this is Device) {
        messageFlow.onEach {
            outbox.emit(it)
        }.launchIn(scope)
    }
    //TODO maybe better create map of all devices to limit copying
    devices.forEach { (token, childDevice) ->
        val flow = if (childDevice is DeviceHub) {
            childDevice.hubMessageFlow(scope)
        } else {
            childDevice.messageFlow
        }
        flow.onEach { deviceMessage ->
            outbox.emit(
                deviceMessage.changeSource { token + it }
            )
        }.launchIn(scope)
    }
    return outbox
}