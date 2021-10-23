rootProject.name = "controls-kt"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("VERSION_CATALOGS")

pluginManagement {
    val toolsVersion = "0.10.5"

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

dependencyResolutionManagement {
    repositories {
        maven("https://repo.kotlin.link")
        mavenCentral()
    }

    versionCatalogs {
        create("npm") {
            from("ru.mipt.npm:version-catalog:0.10.5")
        }
    }
}

include(
    ":controls-core",
    ":controls-tcp",
    ":controls-serial",
    ":controls-server",
    ":controls-opcua",
    ":demo",
    ":magix",
    ":magix:magix-api",
    ":magix:magix-server",
    ":magix:magix-rsocket",
    ":magix:magix-java-client",
    ":magix:magix-zmq",
    ":magix:magix-demo",
    ":controls-magix-client",
    ":motors"
)