package ru.mipt.npm.controls.demo

import com.github.ricky12awesome.jss.encodeToSchema
import com.github.ricky12awesome.jss.globalJson
import ru.mipt.npm.controls.api.DeviceMessage

fun main() {
    val schema = globalJson.encodeToSchema(DeviceMessage.serializer(), generateDefinitions = false)
    println(schema)
}