import scientifik.useCoroutines
import scientifik.useSerialization

plugins {
    id("scientifik.jvm")
    id("scientifik.publish")
    application
}

useSerialization()

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String by extra("1.3.2")

dependencies{
    implementation(project(":dataforge-control-core"))
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
}