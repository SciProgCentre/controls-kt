plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

description = """
   A magix event loop server with web server for visualization.
""".trimIndent()

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String by rootProject.extra

dependencies {
    implementation(project(":controls-core"))
    implementation(project(":controls-tcp"))
    implementation(projects.magix.magixServer)
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
}