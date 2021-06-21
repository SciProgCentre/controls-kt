rootProject.name = "controls"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    val toolsVersion = "0.10.0"

    repositories {
        maven("https://repo.kotlin.link")
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("ru.mipt.npm.gradle.project") version toolsVersion
        id("ru.mipt.npm.gradle.mpp") version toolsVersion
        id("ru.mipt.npm.gradle.jvm") version toolsVersion
        id("ru.mipt.npm.gradle.js") version toolsVersion
    }
}

include(
    ":controls-core",
    ":controls-tcp",
    ":controls-serial",
    ":controls-server",
    ":demo",
    ":magix",
    ":magix:magix-api",
    ":magix:magix-server",
    ":magix:magix-rsocket",
    ":magix:magix-java-client",
    ":magix:magix-zmq",
    ":controls-magix-client",
    ":motors"
)
