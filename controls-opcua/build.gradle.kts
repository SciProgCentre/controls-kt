plugins {
    id("ru.mipt.npm.gradle.jvm")
}

val ktorVersion: String by rootProject.extra

val miloVersion: String = "0.6.3"

dependencies {
    api(project(":controls-core"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${ru.mipt.npm.gradle.KScienceVersions.coroutinesVersion}")

    api("org.eclipse.milo:sdk-client:$miloVersion")
    api("org.eclipse.milo:bsd-parser:$miloVersion")

    api("org.eclipse.milo:sdk-server:$miloVersion")
}