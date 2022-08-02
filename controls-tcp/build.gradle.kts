plugins {
    id("space.kscience.gradle.jvm")
}

val ktorVersion: String by rootProject.extra

dependencies {
    api(project(":controls-core"))
    api("io.ktor:ktor-network:$ktorVersion")
}
