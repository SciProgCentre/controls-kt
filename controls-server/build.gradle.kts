plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

description = """
   A stand-alone device tree web server which also works as magix event dispatcher.
   The server is used to work with stand-alone devices without intermediate control system.
""".trimIndent()

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String = "1.5.3"

dependencies {
    implementation(project(":controls-core"))
    implementation(project(":controls-tcp"))
    implementation(projects.magix.magixServer)
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
}