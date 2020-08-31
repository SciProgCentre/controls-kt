pluginManagement {
    val kotlinVersion = "1.4.0"
    val toolsVersion = "0.6.0"

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
                "kscience.publish", "kscience.mpp", "kscience.jvm", "kscience.js" -> useModule("ru.mipt.npm:gradle-tools:${toolsVersion}")
                "kotlinx-atomicfu" -> useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${requested.version}")
            }
        }
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