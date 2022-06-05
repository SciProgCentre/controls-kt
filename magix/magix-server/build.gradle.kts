plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
    application
}

description = """
    A magix event loop implementation in Kotlin. Includes HTTP/SSE and RSocket routes.
""".trimIndent()

kscience {
    useSerialization{
        json()
    }
}

val dataforgeVersion: String by rootProject.extra
val rsocketVersion: String by rootProject.extra
val ktorVersion: String  = ru.mipt.npm.gradle.KScienceVersions.ktorVersion

dependencies{
    api(projects.magix.magixApi)
    api("io.ktor:ktor-server-cio:$ktorVersion")
    api("io.ktor:ktor-server-websockets:$ktorVersion")
    api("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    api("io.ktor:ktor-server-html-builder:$ktorVersion")

    api("io.rsocket.kotlin:rsocket-ktor-server:$rsocketVersion")
    api("io.rsocket.kotlin:rsocket-transport-ktor-tcp:$rsocketVersion")

    api("org.zeromq:jeromq:0.5.2")
}