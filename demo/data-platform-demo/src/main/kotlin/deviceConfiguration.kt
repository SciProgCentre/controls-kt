package space.kscience.controls.demo

import space.kscience.controls.api.DeviceTree
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.expressions.StateExpression
import space.kscience.controls.opcua.server.read
import space.kscience.controls.tagtable.TagTable
import space.kscience.controls.tagtable.TagTableColumn
import space.kscience.controls.tagtable.TagTableConfiguration
import space.kscience.controls.utilities.Alarm
import space.kscience.controls.utilities.AlarmSetting
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.*


internal fun createDeviceConfiguration(configuration: TagTableConfiguration): ConstructorDeviceConfiguration {

    val alarmSetting = listOf(
        AlarmSetting(-5.0, 5.0, "OUT5"),
        AlarmSetting(-10.0, 10.0, "OUT10")
    )

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
                            properties = tagProperties + ("sum" to expressionPropertyConfiguration),
                            templates = mapOf(
                                "alarm" to TemplateDeviceConfiguration(
                                    type = "controls.utilities.alarm",
                                    parameters = Alarm.buildDeviceMeta(
                                        settings = alarmSetting
                                    )
                                )
                            ),
                            bindings = setOf(
                                ConstructorBinding(
                                    sourceDevice = Name.EMPTY,
                                    sourceProperty = "sum",
                                    targetDevice = Name.of("alarm")
                                )
                            )
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

//internal fun createAlarmConfig(deviceConfig: ConstructorDeviceConfiguration): ConstructorDeviceConfiguration {
//
//    val alarmSetting = listOf(
//        AlarmSetting(-5.0, 5.0, "OUT5"),
//        AlarmSetting(-10.0, 10.0, "OUT10")
//    )
//
//    fun visit(deviceName: Name, source: ConstructorDeviceConfiguration): ConstructorDeviceConfiguration {
//        val templates = if (source.properties.keys.contains("sum")){
//            mapOf(
//                "alarm" to TemplateDeviceConfiguration(
//                    type = "controls.utilities.alarm",
//                    parameters = Alarm.buildDeviceMeta(
//                        settings = alarmSetting
//                    )
//                )
//            )
//        } else{
//             emptyMap()
//        }
//
//        return ConstructorDeviceConfiguration(
//            properties = emptyMap(),
//            devices = source.devices.mapValues { visit(deviceName + it.key, it.value) },
//            templates = templates
//        )
//    }
//
//    return visit(Name.of("devices"), deviceConfig)
//
//}

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