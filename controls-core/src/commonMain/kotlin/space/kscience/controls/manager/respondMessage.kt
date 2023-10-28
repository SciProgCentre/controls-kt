package space.kscience.controls.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
            if (request.value == null) {
                invalidate(request.property)
            } else {
                writeProperty(request.property, request.value)
            }
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
 * Process incoming [DeviceMessage], using hub naming to evaluate target.
 */
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
 * Collect all messages from given [DeviceHub], applying proper relative names.
 */
public fun DeviceHub.hubMessageFlow(scope: CoroutineScope): Flow<DeviceMessage> {

    //TODO could we avoid using downstream scope?
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