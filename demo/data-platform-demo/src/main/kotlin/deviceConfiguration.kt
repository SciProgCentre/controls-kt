package space.kscience.controls.demo

import space.kscience.controls.api.DeviceTree
import space.kscience.controls.constructor.ConstructorDeviceConfiguration
import space.kscience.controls.constructor.ExpressionValueStateFactory
import space.kscience.controls.constructor.PropertyConfiguration
import space.kscience.controls.constructor.expressions.StateExpression
import space.kscience.controls.opcua.server.read
import space.kscience.controls.tagtable.TagTable
import space.kscience.controls.tagtable.TagTableColumn
import space.kscience.controls.tagtable.TagTableConfiguration
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.*


internal fun createDeviceConfiguration(configuration: TagTableConfiguration): ConstructorDeviceConfiguration {
    val blocks = configuration.properties.mapKeys { it.key.parseAsName() }.entries
        .groupBy { (tag, property) ->
            tag.first()
        }.entries.associate { (source, properties) ->
            val devices: Map<String, ConstructorDeviceConfiguration> = buildMap {
                properties.chunked(10).forEachIndexed { index, chunk: List<Map.Entry<Name, TagTableColumn>> ->

                    val tagProperties = chunk.associate { (tag, _) ->
                        tag.cutFirst().toString() to PropertyConfiguration(
                            type = TagTable.TAG_TABLE_FACTORY_TYPE,
                            parameters = Meta {
                                set(TagTable.ValueFactorySpec.tag, tag.toString())
                            }
                        )
                    }

                    val expression = StateExpression.Nary(
                        operation = "sum",
                        arguments = tagProperties.mapValues { (_, pc) ->
                            StateExpression.State(
                                valueStateType = pc.type,
                                parameters = pc.parameters,
                            )
                        }
                    )

                    val expressionPropertyConfiguration = PropertyConfiguration(
                        type = "expression",
                        parameters = ExpressionValueStateFactory.buildMeta(expression)
                    )

                    put(
                        "part[$index]",
                        ConstructorDeviceConfiguration(
                            properties = tagProperties + ("sum" to expressionPropertyConfiguration)
                        )
                    )
                }
            }
            "aggregate-${source.toStringUnescaped()}" to ConstructorDeviceConfiguration(
                properties = emptyMap(),
                devices = devices
            )
        }

    return ConstructorDeviceConfiguration(
        properties = emptyMap(),
        devices = blocks
    )
}

private suspend fun DeviceTree.snapshotValues(): Map<Name, Meta> = buildMap {
    device?.let { device ->
        device.propertyDescriptors.forEach {
            val value = device.read(it)
            put(Name.of(it.name), value)
        }
    }
    children.forEach { (childName, tree) ->
        putAll(tree.snapshotValues().mapKeys { Name.of(childName) + it.key })
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