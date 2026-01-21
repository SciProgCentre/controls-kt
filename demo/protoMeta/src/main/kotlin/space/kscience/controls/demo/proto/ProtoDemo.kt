package space.kscience.controls.demo.proto

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.ports.KtorPortsPlugin
import space.kscience.controls.ports.Ports
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.invoke
import space.kscience.controls.ports.AsynchronousPort
import space.kscience.dataforge.context.gather
import space.kscience.dataforge.context.Factory
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.io.readByteArray
import kotlinx.io.Buffer

import space.kscience.controls.proto.generation.MetaRustGenerator
import java.io.File

fun main() = runBlocking {
    println("Generating Rust code...")
    MetaRustGenerator.generateToFile(DemoDevice, "build/generated/rust/device.rs")
    println("Rust code generated at build/generated/rust/device.rs")

    val context = Context("ProtoDemo") {
        plugin(Ports)
        plugin(KtorPortsPlugin)
    }

    val deviceManager = context.request(DeviceManager)

    // Configuration for the device. 
    val device = deviceManager.install("demo", DemoDevice, Meta {
        "port" put {
            "type" put "controls.ports.ktor.udp"
            "remoteHost" put "10.0.0.2"
            "remotePort" put 1337
            "localHost" put "10.0.0.1"
            "localPort" put 4242
        }
    })

    println("Device started: $device")

    // Send data periodically
    launch {
        var counter = 0
        while(true) {
            try {
                // POST: Update 'channel' property
                println("Sending POST channel=$counter...")
                device.send(Meta { 
                    "method" put "POST"
                    "channel" put ""
                    "type" put "int"
                }, java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(counter).array())

                delay(500)

                // GET: Read 'voltage' property
                println("Sending GET voltage...")
                device.send(Meta {
                    "method" put "GET"
                    "voltage" put null // Key presence triggers read
                })

                println("Packets sent")
                counter++
            } catch (e: Exception) {
                println("Send failed: ${e.message}")
            }
            delay(1000)
        }
    }

    // Keep the demo running
    delay(20000)
}
