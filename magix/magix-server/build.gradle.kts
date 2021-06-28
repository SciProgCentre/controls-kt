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
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")

    implementation("io.rsocket.kotlin:rsocket-core:$rsocketVersion")
    implementation("io.rsocket.kotlin:rsocket-transport-ktor-server:$rsocketVersion")

    implementation("org.zeromq:jeromq:0.5.2")
}