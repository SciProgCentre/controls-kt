package space.kscience.dataforge.control.demo

import com.github.ricky12awesome.jss.encodeToSchema
import com.github.ricky12awesome.jss.globalJson
import space.kscience.dataforge.control.messages.DeviceMessage

fun main() {
    val schema = globalJson.encodeToSchema(DeviceMessage.serializer(), generateDefinitions = false)
    println(schema)
}