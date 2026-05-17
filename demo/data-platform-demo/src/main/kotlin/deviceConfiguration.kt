package space.kscience.controls.demo

import space.kscience.controls.constructor.DeviceConfiguration
import space.kscience.controls.constructor.DeviceGroup
import space.kscience.controls.constructor.PropertyConfiguration
import space.kscience.controls.dataplatform.DataPlatform
import space.kscience.controls.dataplatform.DataPlatformConfiguration
import space.kscience.controls.dataplatform.PlatformProperty
import space.kscience.controls.dataplatform.buildDeviceGroup
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.installNode
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.cutFirst
import space.kscience.dataforge.names.first
import space.kscience.dataforge.names.parseAsName


internal fun createDeviceConfiguration(configuration: DataPlatformConfiguration): DeviceConfiguration {
    val blocks = configuration.properties.mapKeys { it.key.parseAsName() }.entries
        .groupBy { (tag, property) ->
            tag.first().toString()
        }.mapValues { (_, properties) ->
            val devices: Map<String, DeviceConfiguration> = buildMap {
                properties.chunked(10).forEachIndexed { index, chunk: List<Map.Entry<Name, PlatformProperty>> ->
                    put(
                        "part[$index]",
                        DeviceConfiguration(
                            properties = chunk.associate { (tag, _) ->
                                tag.cutFirst().toString() to PropertyConfiguration(
                                    type = DataPlatform.PLATFORM_VALUE_FACTORY_TYPE,
                                    parameters = Meta {
                                        set(DataPlatform.tag, tag.toString())
                                    }
                                )
                            }
                        )
                    )
                }
            }
            DeviceConfiguration(
                properties = emptyMap(),
                devices = devices
            )
        }

    return DeviceConfiguration(
        properties = emptyMap(),
        devices = blocks
    )
}

fun DeviceManager.installFromConfiguration(
    platform: DataPlatform,
    configuration: DataPlatformConfiguration,
    deviceName: String
): DeviceGroup {
    val deviceConfiguration = createDeviceConfiguration(configuration)
    val device = platform.buildDeviceGroup(deviceConfiguration)
    return installNode(deviceName, device)
}