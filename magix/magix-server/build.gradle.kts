plugins {
    id("ru.mipt.npm.jvm")
    id("ru.mipt.npm.publish")
    application
}

kscience {
    useSerialization{
        json()
    }
}

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String by rootProject.extra
val rsocketVersion: String by rootProject.extra

dependencies{
    api(project(":magix:magix-api"))
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")

    implementation("io.rsocket.kotlin:rsocket-core:$rsocketVersion")
    implementation("io.rsocket.kotlin:rsocket-transport-ktor-server:$rsocketVersion")
}