pluginManagement {
    val kotlinVersion = "1.4.0"
    val toolsVersion = "0.6.0-dev-4"

    repositories {
        mavenLocal()
        jcenter()
        gradlePluginPortal()
        maven("https://kotlin.bintray.com/kotlinx")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        maven("https://dl.bintray.com/mipt-npm/dataforge")
        maven("https://dl.bintray.com/mipt-npm/kscience")
        maven("https://dl.bintray.com/mipt-npm/dev")
    }

    plugins {
        id("ru.mipt.npm.mpp") version toolsVersion
        id("ru.mipt.npm.jvm") version toolsVersion
        id("ru.mipt.npm.js") version toolsVersion
        id("ru.mipt.npm.publish") version toolsVersion
        kotlin("jvm") version kotlinVersion
        kotlin("js") version kotlinVersion
    }
}

rootProject.name = "dataforge-control"

include(
    ":dataforge-device-core",
    ":dataforge-device-serial",
    ":dataforge-device-server",
    ":dataforge-device-client",
    ":demo",
    ":motors"
)

//includeBuild("../dataforge-core")
//includeBuild("../plotly.kt")