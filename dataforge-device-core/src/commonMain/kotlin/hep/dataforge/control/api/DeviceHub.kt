package hep.dataforge.control.api

import hep.dataforge.control.controllers.DeviceMessage
import hep.dataforge.io.Envelope
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.get
import hep.dataforge.meta.string
import hep.dataforge.names.Name
import hep.dataforge.names.toName
import hep.dataforge.provider.Provider

/**
 * A hub that could locate multiple devices and redirect actions to them
 */
interface DeviceHub : Provider {
    val devices: Map<Name, Device>//TODO use token instead of Names

    override val defaultTarget: String get() = Device.DEVICE_TARGET

    override val defaultChainTarget: String get() = Device.DEVICE_TARGET

    override fun provideTop(target: String): Map<Name, Any> {
        if (target == Device.DEVICE_TARGET) {
            return devices
        } else {
            throw IllegalArgumentException("Target $target is not supported for $this")
        }
    }

    companion object {

    }
}

operator fun DeviceHub.get(deviceName: Name) =
    devices[deviceName] ?: error("Device with name $deviceName not found in $this")

operator fun DeviceHub.get(deviceName: String) = get(deviceName.toName())

suspend fun DeviceHub.getProperty(deviceName: Name, propertyName: String): MetaItem<*> =
    this[deviceName].getProperty(propertyName)

suspend fun DeviceHub.setProperty(deviceName: Name, propertyName: String, value: MetaItem<*>) {
    this[deviceName].setProperty(propertyName, value)
}

suspend fun DeviceHub.execute(deviceName: Name, command: String, argument: MetaItem<*>?): MetaItem<*>? =
    this[deviceName].execute(command, argument)

suspend fun DeviceHub.respondMessage(request: DeviceMessage): DeviceMessage {
    return try {
        val targetName = request.target?.toName() ?: Name.EMPTY
        val device = this[targetName]
        Device.respondMessage(device, targetName.toString(), request)
    } catch (ex: Exception) {
        DeviceMessage.fail {
            comment = ex.message
        }
    }
}

suspend fun DeviceHub.respond(request: Envelope): Envelope {
    val target = request.meta[DeviceMessage.TARGET_KEY].string ?: defaultTarget
    val device = this[target.toName()]

    return Device.respond(device, target, request)
}