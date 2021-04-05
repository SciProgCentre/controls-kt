plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String  = "1.5.3"

dependencies{
    implementation(project(":controls-core"))
    implementation(project(":controls-tcp"))
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
}