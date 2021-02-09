package hep.dataforge.control.api

import hep.dataforge.meta.MetaItem
import hep.dataforge.names.*
import hep.dataforge.provider.Provider

/**
 * A hub that could locate multiple devices and redirect actions to them
 */
public interface DeviceHub : Provider {
    public val deviceName: String

    public val devices: Map<NameToken, Device>

    override val defaultTarget: String get() = Device.DEVICE_TARGET

    override val defaultChainTarget: String get() = Device.DEVICE_TARGET

    override fun content(target: String): Map<Name, Any> {
        if (target == Device.DEVICE_TARGET) {
            return buildMap {
                fun putAll(prefix: Name, hub: DeviceHub) {
                    hub.devices.forEach {
                        put(prefix + it.key, it.value)
                    }
                }

                devices.forEach {
                    val name = it.key.asName()
                    put(name, it.value)
                    (it.value as? DeviceHub)?.let { hub ->
                        putAll(name, hub)
                    }
                }
            }
        } else {
            throw IllegalArgumentException("Target $target is not supported for $this")
        }
    }

    public companion object {

    }
}

public operator fun DeviceHub.get(nameToken: NameToken): Device =
    devices[nameToken] ?: error("Device with name $nameToken not found in $this")

public operator fun DeviceHub.get(name: Name): Device? = when {
    name.isEmpty() -> this as? Device
    name.length == 1 -> get(name.firstOrNull()!!)
    else -> (get(name.firstOrNull()!!) as? DeviceHub)?.get(name.cutFirst())
}

public operator fun DeviceHub.get(deviceName: String): Device? = get(deviceName.toName())

public suspend fun DeviceHub.getProperty(deviceName: Name, propertyName: String): MetaItem? =
    this[deviceName]?.getProperty(propertyName)

public suspend fun DeviceHub.setProperty(deviceName: Name, propertyName: String, value: MetaItem) {
    this[deviceName]?.setProperty(propertyName, value)
}

public suspend fun DeviceHub.execute(deviceName: Name, command: String, argument: MetaItem?): MetaItem? =
    this[deviceName]?.execute(command, argument)


//suspend fun DeviceHub.respond(request: Envelope): EnvelopeBuilder {
//    val target = request.meta[DeviceMessage.TARGET_KEY].string ?: defaultTarget
//    val device = this[target.toName()]
//
//    return device.respond(device, target, request)
//}