rootProject.name = "controls.kt"

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
    ":magix:magix-service",
    ":magix:magix-java-client",
    ":controls-magix-client",
    ":motors"
)

//includeBuild("../dataforge-core")
//includeBuild("../plotly.kt")
include("magix-java-client")
