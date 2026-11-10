package space.kscience.controls.demo

import space.kscience.controls.api.DeviceTree
import space.kscience.controls.constructor.ConstructorBinding
import space.kscience.controls.constructor.ConstructorDeviceConfiguration
import space.kscience.controls.constructor.TemplateDeviceConfiguration
import space.kscience.controls.constructor.ValueStateConfiguration
import space.kscience.controls.expressions.ValueStateExpression
import space.kscience.controls.expressions.expression
import space.kscience.controls.expressions.math
import space.kscience.controls.opcua.server.read
import space.kscience.controls.tagtable.TagTable
import space.kscience.controls.tagtable.TagTableColumn
import space.kscience.controls.tagtable.TagTableConfiguration
import space.kscience.controls.tagtable.TagTablePlugin
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

    val alarmsConfigs = mutableMapOf<String, ValueStateConfiguration>()

    val blocks = configuration.tags.mapKeys { it.key.parseAsName() }.entries
        .groupBy { (tag, property) ->
            tag.first()
        }.entries.associate { (source, properties) ->
            val aggregateName = "aggregate-${source.toStringUnescaped()}"

            val devices: Map<String, ConstructorDeviceConfiguration> = buildMap {
                properties.chunked(10).forEachIndexed { index, chunk: List<Map.Entry<Name, TagTableColumn>> ->

                    val tagProperties = chunk.associate { (tag, _) ->
                        tag.cutFirst().toString() to ValueStateConfiguration(
                            type = TagTable.TAG_TABLE_FACTORY_TYPE,
                            parameters = Meta {
                                set(TagTablePlugin.ValueFactorySpec.tag, tag.toString())
                            }
                        )
                    }

                    val expression = ValueStateExpression.Function(
                        operation = "sum",
                        arguments = tagProperties.mapValues { (_, pc) ->
                            ValueStateExpression.State(
                                valueStateType = pc.type,
                                parameters = pc.parameters,
                            )
                        }
                    )

                    val expressionValueStateConfiguration = ValueStateConfiguration.expression(expression)

                    val mathValueStateConfiguration = ValueStateConfiguration.math(
                        "diff(integrateMinute(arg))",
                        mapOf("arg" to tagProperties.values.first())
                    )

                    val partName = "part[$index]"

                    put(
                        partName,
                        ConstructorDeviceConfiguration(
                            properties = tagProperties + ("sum" to expressionValueStateConfiguration) + ("integral" to mathValueStateConfiguration),
                            components = mapOf(
                                "alarm" to TemplateDeviceConfiguration(
                                    type = "controls.utilities.alarm",
                                    parameters = Alarm.buildDeviceMeta(
                                        settings = alarmSetting
                                    ){
                                        "widget" put "alarm"
                                    },
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
                    val fullName = Name.of(aggregateName, partName)
                    alarmsConfigs[fullName.toStringUnescaped()] = ValueStateConfiguration.deviceProperty(
                        deviceName = Name.of( aggregateName, partName, "alarm").toString(),
                        propertyName = "state",
                        metadata = { "widget" put "alarm.state" }
                    )
                }
            }
            aggregateName to ConstructorDeviceConfiguration(
                devices = devices
            )
        }

    val alarms = ConstructorDeviceConfiguration(
        properties = alarmsConfigs,
    )

    return ConstructorDeviceConfiguration(
        devices = blocks + ("alarms" to alarms)
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