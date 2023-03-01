plugins {
    id("space.kscience.gradle.jvm")
}

val ktorVersion: String by rootProject.extra

dependencies {
    api(projects.controlsCore)
    api("io.ktor:ktor-network:$ktorVersion")
}
