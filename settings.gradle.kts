pluginManagement {
    val kotlinVersion = "1.3.72"
    val toolsVersion = "0.5.0"

    repositories {
        mavenLocal()
        jcenter()
        gradlePluginPortal()
        maven("https://kotlin.bintray.com/kotlinx")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        maven("https://dl.bintray.com/mipt-npm/dataforge")
        maven("https://dl.bintray.com/mipt-npm/scientifik")
        maven("https://dl.bintray.com/mipt-npm/dev")
    }

    plugins {


        kotlin("jvm") version kotlinVersion
        id("scientifik.mpp") version toolsVersion
        id("scientifik.jvm") version toolsVersion
        id("scientifik.js") version toolsVersion
        id("scientifik.publish") version toolsVersion
    }

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "scientifik.publish", "scientifik.mpp", "scientifik.jvm", "scientifik.js" -> useModule("scientifik:gradle-tools:${toolsVersion}")
                "kotlinx-atomicfu" -> useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${requested.version}")
            }
        }
    }
}

rootProject.name = "dataforge-device"

include(
    ":dataforge-device-core",
    ":dataforge-device-server",
    ":dataforge-device-client",
    ":demo"
)

//includeBuild("../dataforge-core")
//includeBuild("../plotly.kt")