package hep.dataforge.control.api

import hep.dataforge.meta.MetaItem
import hep.dataforge.names.Name
import hep.dataforge.names.NameToken
import hep.dataforge.names.asName
import hep.dataforge.names.toName
import hep.dataforge.provider.Provider

/**
 * A hub that could locate multiple devices and redirect actions to them
 */
interface DeviceHub : Provider {
    val devices: Map<NameToken, Device>

    override val defaultTarget: String get() = Device.DEVICE_TARGET

    override fun provideTop(target: String): Map<Name, Any> {
        if (target == Device.DEVICE_TARGET) {
            return devices.mapKeys { it.key.asName() }
        } else {
            throw IllegalArgumentException("Target $target is not supported for $this")
        }
    }

    companion object {

    }
}

/**
 * Resolve the device by its full name if it is present. Hubs are resolved recursively.
 */
fun DeviceHub.getDevice(name: Name): Device = when (name.length) {
    0 -> (this as? Device) ?: error("The DeviceHub is resolved by name but it is not a Device")
    1 -> {
        val token = name.first()!!
        devices[token] ?: error("Device with name $token not found in the hub $this")
    }
    else -> {
        val hub = getDevice(name.cutLast()) as? DeviceHub
            ?: error("The device with name ${name.cutLast()} does not exist or is not a hub")
        hub.getDevice(name.last()!!.asName())
    }
}


fun DeviceHub.getDevice(deviceName: String) = getDevice(deviceName.toName())

suspend fun DeviceHub.getProperty(deviceName: String, propertyName: String): MetaItem<*> =
    getDevice(deviceName).getProperty(propertyName)

suspend fun DeviceHub.setProperty(deviceName: String, propertyName: String, value: MetaItem<*>) {
    getDevice(deviceName).setProperty(propertyName, value)
}

suspend fun DeviceHub.exec(deviceName: String, command: String, argument: MetaItem<*>?): MetaItem<*>? =
    getDevice(deviceName).exec(command, argument)