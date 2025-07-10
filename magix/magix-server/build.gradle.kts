import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    A magix event loop implementation in Kotlin. Includes HTTP/SSE and RSocket routes.
""".trimIndent()

val dataforgeVersion: String by rootProject.extra
//val ktorVersion: String  = space.kscience.gradle.KScienceVersions.ktorVersion

kscience {
    jvm()
    useSerialization{
        json()
        cbor()
        protobuf()
    }

    jvmMain{
        api(projects.magix.magixApi)
        api(project.dependencies.platform(spclibs.ktor.bom))
        api("io.ktor:ktor-server-cio")
        api("io.ktor:ktor-server-websockets")
        api("io.ktor:ktor-server-content-negotiation")
        api("io.ktor:ktor-serialization-kotlinx-json")
        api("io.ktor:ktor-server-html-builder")

        api(libs.rsocket.ktor.server)
        api(libs.rsocket.transport.ktor.tcp)
        api(spclibs.kotlinx.io.core)
    }

}


readme{
    maturity = Maturity.EXPERIMENTAL
}