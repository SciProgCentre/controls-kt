package space.kscience.controls.demo.proto

import space.kscience.controls.proto.generation.protocolGeneration
import java.io.File

private val demoProtocolGeneration = protocolGeneration(DemoDevice) {
    rustCrate(
        outputDirectory = File("/Users/igorzhukov/Documents/Rust/RticProtocol/src/communication"),
        moduleName = "device",
        crateName = "demo_device_protocol",
    )
}

public fun main() {
    demoProtocolGeneration.generate(::println)
}
