package space.kscience.controls.demo

import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request

fun main() {
    val context = Context {
        plugin(DeviceManager)
    }
    val deviceManager = context.request(DeviceManager)

    deviceManager.setupTestDevices()

    do{
        val str = readlnOrNull()
    } while (str != "exit")

    context.close()
}