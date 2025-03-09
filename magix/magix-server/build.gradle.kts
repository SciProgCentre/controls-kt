import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    A magix event loop implementation in Kotlin. Includes HTTP/SSE and RSocket routes.
""".trimIndent()

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String  = space.kscience.gradle.KScienceVersions.ktorVersion

kscience {
    jvm()
    useSerialization{
        json()
    }

    jvmMain{
        api(projects.magix.magixApi)
        api("io.ktor:ktor-server-cio:$ktorVersion")
        api("io.ktor:ktor-server-websockets:$ktorVersion")
        api("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        api("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
        api("io.ktor:ktor-server-html-builder:$ktorVersion")

        api(libs.rsocket.ktor.server)
        api(libs.rsocket.transport.ktor.tcp)
    }

}


readme{
    maturity = Maturity.EXPERIMENTAL
}