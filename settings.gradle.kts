pluginManagement {
    val kotlinVersion = "1.4.30"
    val toolsVersion = "0.7.6"

    repositories {
        mavenLocal()
        jcenter()
        gradlePluginPortal()
        //maven("https://kotlin.bintray.com/kotlinx")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        maven("https://dl.bintray.com/mipt-npm/dataforge")
        maven("https://dl.bintray.com/mipt-npm/kscience")
        maven("https://dl.bintray.com/mipt-npm/dev")
    }

    plugins {
        id("ru.mipt.npm.project") version toolsVersion
        id("ru.mipt.npm.mpp") version toolsVersion
        id("ru.mipt.npm.jvm") version toolsVersion
        id("ru.mipt.npm.js") version toolsVersion
        id("ru.mipt.npm.publish") version toolsVersion
        kotlin("jvm") version kotlinVersion
        kotlin("js") version kotlinVersion
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
    ":motors"
)

//includeBuild("../dataforge-core")
//includeBuild("../plotly.kt")
include("magix-java-client")
