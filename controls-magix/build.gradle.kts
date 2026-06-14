import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Magix service for binding controls devices (both as RPC client and server)
""".trimIndent()

kscience {
    jvm()
    js()
    native()
//    wasm()
    useCoroutines()
    useSerialization {
        json()
    }

    commonMain {
        api(projects.magix.magixApi)
        api(projects.controlsCore)
        api(libs.uuid)
    }

    jvmTest{
        implementation(spclibs.logback.classic)
        implementation(projects.magix.magixServer)
        implementation(projects.magix.magixRsocket)
        implementation("io.ktor:ktor-server-cio")
        implementation("io.ktor:ktor-server-websockets")
        implementation("io.ktor:ktor-client-cio")
    }
}

readme {
    maturity = Maturity.EXPERIMENTAL

    feature("magixService", ref = "src/commonMain/kotlin/space/kscience/controls/client/controlsMagix.kt"){
        """
            Connect a `DeviceManager` with one or many devices to the Magix endpoint
        """.trimIndent()
    }

    feature("deviceClient", ref = "src/commonMain/kotlin/space/kscience/controls/client/DeviceClient.kt"){
        """
            A remote connector to Controls-kt device via Magix
        """.trimIndent()
    }
}