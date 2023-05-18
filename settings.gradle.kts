rootProject.name = "controls-kt"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {

    val toolsVersion: String by extra

    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.kotlin.link")
    }

    plugins {
        id("space.kscience.gradle.project") version toolsVersion
        id("space.kscience.gradle.mpp") version toolsVersion
        id("space.kscience.gradle.jvm") version toolsVersion
        id("space.kscience.gradle.js") version toolsVersion
        id("org.openjfx.javafxplugin") version "0.0.13"
    }
}

dependencyResolutionManagement {

    val toolsVersion: String by extra

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.kotlin.link")
    }

    versionCatalogs {
        create("spclibs") {
            from("space.kscience:version-catalog:$toolsVersion")
        }
    }
}

include(
    ":controls-core",
    ":controls-ktor-tcp",
    ":controls-serial",
    ":controls-pi",
    ":controls-server",
    ":controls-opcua",
    ":controls-modbus",
//    ":controls-mongo",
    ":controls-storage",
    ":controls-storage:controls-xodus",
    ":magix",
    ":magix:magix-api",
    ":magix:magix-server",
    ":magix:magix-rsocket",
    ":magix:magix-java-client",
    ":magix:magix-zmq",
    ":magix:magix-rabbit",
    ":magix:magix-mqtt",

//    ":magix:magix-storage",
    ":magix:magix-storage:magix-storage-xodus",
    ":controls-magix-client",
    ":demo:all-things",
    ":demo:many-devices",
    ":demo:magix-demo",
    ":demo:car",
    ":demo:motors",
    ":demo:echo",
    ":demo:mks-pdr900"
)
