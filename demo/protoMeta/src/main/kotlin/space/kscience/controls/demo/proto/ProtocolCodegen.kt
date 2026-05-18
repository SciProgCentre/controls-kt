package space.kscience.controls.demo.proto

import space.kscience.controls.proto.generation.protocolGeneration
import java.io.File

private val demoProtocolGeneration = protocolGeneration(DemoDevice) {
    // rustCrate(
    //     outputDirectory = File("/Users/igorzhukov/Documents/Rust/RticProtocol/src/communication"),
    //     moduleName = "device",
    //     crateName = "demo_device_protocol",
    // )
    c(
        // outputDirectory = File("build/generated/protocol/c/demo_device_protocol"),
        outputDirectory = File("/Users/igorzhukov/Documents/STM32/ProtoSTM32/Core/Src/Communication"),
        moduleName = "device",
        cleanOutputDirectory = true,
    )
}

public fun main() {
    demoProtocolGeneration.generate(::println)
}
