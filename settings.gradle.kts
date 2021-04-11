pluginManagement {
    val kotlinVersion = "1.5.0-M2"
    val toolsVersion = "0.9.5-dev"

    repositories {
        mavenLocal()
        maven("https://repo.kotlin.link")
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("ru.mipt.npm.gradle.project") version toolsVersion
        id("ru.mipt.npm.gradle.mpp") version toolsVersion
        id("ru.mipt.npm.gradle.jvm") version toolsVersion
        id("ru.mipt.npm.gradle.js") version toolsVersion
        id("ru.mipt.npm.gradle.publish") version toolsVersion
        kotlin("jvm") version kotlinVersion
        kotlin("js") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
    }
}

rootProject.name = "controls.kt"

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
    ":controls-magix-server",
    ":motors"
)

//includeBuild("../dataforge-core")
//includeBuild("../plotly.kt")
include("magix-java-client")
