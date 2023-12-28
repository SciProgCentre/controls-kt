package space.kscience.controls.api

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.*
import space.kscience.dataforge.provider.Provider

/**
 * A hub that could locate multiple devices and redirect actions to them
 */
public interface DeviceHub : Provider {
    public val devices: Map<NameToken, Device>

    override val defaultTarget: String get() = Device.DEVICE_TARGET

    override val defaultChainTarget: String get() = Device.DEVICE_TARGET

    /**
     * List all devices, including sub-devices
     */
    public fun buildDeviceTree(): Map<Name, Device> = buildMap {
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

    override fun content(target: String): Map<Name, Any> = if (target == Device.DEVICE_TARGET) {
        buildDeviceTree()
    } else {
        emptyMap()
    }

    public companion object
}


public operator fun DeviceHub.get(nameToken: NameToken): Device =
    devices[nameToken] ?: error("Device with name $nameToken not found in $this")

public fun DeviceHub.getOrNull(name: Name): Device? = when {
    name.isEmpty() -> this as? Device
    name.length == 1 -> get(name.firstOrNull()!!)
    else -> (get(name.firstOrNull()!!) as? DeviceHub)?.getOrNull(name.cutFirst())
}

public operator fun DeviceHub.get(name: Name): Device =
    getOrNull(name) ?: error("Device with name $name not found in $this")

public fun DeviceHub.getOrNull(nameString: String): Device? = getOrNull(Name.parse(nameString))

public operator fun DeviceHub.get(nameString: String): Device =
    getOrNull(nameString) ?: error("Device with name $nameString not found in $this")

public suspend fun DeviceHub.readProperty(deviceName: Name, propertyName: String): Meta =
    this[deviceName].readProperty(propertyName)

public suspend fun DeviceHub.writeProperty(deviceName: Name, propertyName: String, value: Meta) {
    this[deviceName].writeProperty(propertyName, value)
}

public suspend fun DeviceHub.execute(deviceName: Name, command: String, argument: Meta?): Meta? =
    this[deviceName].execute(command, argument)


//suspend fun DeviceHub.respond(request: Envelope): EnvelopeBuilder {
//    val target = request.meta[DeviceMessage.TARGET_KEY].string ?: defaultTarget
//    val device = this[target.toName()]
//
//    return device.respond(device, target, request)
//}