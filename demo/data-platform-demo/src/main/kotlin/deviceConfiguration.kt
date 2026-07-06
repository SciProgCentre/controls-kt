package space.kscience.controls.demo

import space.kscience.controls.api.DeviceTree
import space.kscience.controls.constructor.DeviceConfiguration
import space.kscience.controls.constructor.PropertyConfiguration
import space.kscience.controls.opcua.server.read
import space.kscience.controls.tagtable.PlcTableConfiguration
import space.kscience.controls.tagtable.TagTable
import space.kscience.controls.tagtable.TagTableColumn
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.*


@OptIn(DFExperimental::class)
internal fun createDeviceConfiguration(configuration: PlcTableConfiguration): DeviceConfiguration {
    val blocks = configuration.properties.mapKeys { it.key.parseAsName() }.entries
        .groupBy { (tag, property) ->
            tag.first()
        }.entries.associate { (source, properties) ->
            val devices: Map<String, DeviceConfiguration> = buildMap {
                properties.chunked(10).forEachIndexed { index, chunk: List<Map.Entry<Name, TagTableColumn>> ->
                    put(
                        "part[$index]",
                        DeviceConfiguration(
                            properties = chunk.associate { (tag, _) ->
                                tag.cutFirst().toString() to PropertyConfiguration(
                                    type = TagTable.TAG_TABLE_FACTORY_TYPE,
                                    parameters = Meta {
                                        set(TagTable.ValueFactorySpec.tag, tag.toString())
                                    }
                                )
                            }
                        )
                    )
                }
            }
            "aggregate-${source.toStringUnescaped()}" to DeviceConfiguration(
                properties = emptyMap(),
                devices = devices
            )
        }

    return DeviceConfiguration(
        properties = emptyMap(),
        devices = blocks
    )
}

private suspend fun DeviceTree.snapshotValues(): Map<Name, Meta> = buildMap {
    device?.let { device ->
        device.propertyDescriptors.forEach {
            val value = device.read(it)
            put(it.name.asName(), value)
        }
    }
    children.forEach { (childName, tree) ->
        putAll(tree.snapshotValues().mapKeys { childName.asName() + it.key })
    }
}

//fun TagTablePlugin.installFromConfiguration(
//    deviceName: String,
//    configuration: TagTableConfiguration,
//): DeviceConstructor {
//
//    val deviceConfiguration = createDeviceConfiguration(configuration)
//    Path("data/device-config.json").writeText(
//        json.encodeToString(
//            DeviceConfiguration.serializer(),
//            deviceConfiguration
//        )
//    )
//
//    return deviceManager.install(deviceName, deviceConfiguration)
//}