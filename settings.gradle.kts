pluginManagement {
    val kotlinVersion = "1.3.61"
    val toolsVersion = "0.3.2"

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
            }
        }
    }
}

rootProject.name = "dataforge-control"

include(
    ":dataforge-control-core"
)

includeBuild("../dataforge-core")