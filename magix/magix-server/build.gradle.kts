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
    api(project(":magix:magix-api"))
    api("io.ktor:ktor-server-cio:$ktorVersion")
    api("io.ktor:ktor-websockets:$ktorVersion")
    api("io.ktor:ktor-serialization:$ktorVersion")
    api("io.ktor:ktor-html-builder:$ktorVersion")

    api("io.rsocket.kotlin:rsocket-core:$rsocketVersion")
    api("io.rsocket.kotlin:rsocket-transport-ktor-server:$rsocketVersion")

    api("org.zeromq:jeromq:0.5.2")
}