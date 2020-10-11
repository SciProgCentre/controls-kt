plugins {
    id("ru.mipt.npm.jvm")
    id("ru.mipt.npm.publish")
}

kscience {
    useSerialization()
}

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String by rootProject.extra

dependencies{
    implementation(project(":dataforge-device-core"))
    implementation(project(":dataforge-device-tcp"))
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
}