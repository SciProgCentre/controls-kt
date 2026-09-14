package space.kscience.controls.manager

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
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
 * Process incoming [DeviceMessage], using tree naming to find target.
 * If the `targetDevice` is `null`, then the message is sent to each device in this tree
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

/**
 * Collect all messages from given [DeviceTree], applying proper relative names.
 * Follow replacements reported by [DeviceTree.deviceFlow] and [DeviceTree.childrenFlow].
 * Updating the tree does not wait for message subscriptions to be installed.
 * This flow does not add replay to device message flows.
 */
public fun DeviceTree.messageFlow(): Flow<DeviceMessage> = channelFlow {
    launch(start = CoroutineStart.UNDISPATCHED) {
        var currentDevice: Device? = null
        var deviceJob: Job? = null
        deviceFlow().collect { device ->
            if (device !== currentDevice) {
                deviceJob?.cancelAndJoin()
                currentDevice = device
                deviceJob = device?.let {
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        it.messageFlow.collect { message -> send(message) }
                    }
                }
            }
        }
    }

    val subscriptions = HashMap<String, Pair<DeviceTree, Job>>()
    childrenFlow().collect { children ->
        val iterator = subscriptions.iterator()
        while (iterator.hasNext()) {
            val (name, subscription) = iterator.next()
            if (children[name] !== subscription.first) {
                subscription.second.cancelAndJoin()
                iterator.remove()
            }
        }
        children.forEach { (name, child) ->
            if (name !in subscriptions) {
                val prefix = NameToken(name)
                val job = launch {
                    child.messageFlow().collect { message ->
                        send(message.changeSource { prefix + it })
                    }
                }
                subscriptions[name] = child to job
            }
        }
    }
}
