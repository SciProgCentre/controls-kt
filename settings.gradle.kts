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
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
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

            library("kotlinx-coroutines-jdk9", "org.jetbrains.kotlinx", "kotlinx-coroutines-jdk9").versionRef("kotlinx-coroutines")

            library("ktor-client-core", "io.ktor", "ktor-client-core").versionRef("ktor")
            library("ktor-client-cio", "io.ktor", "ktor-client-cio").versionRef("ktor")
            library("ktor-network", "io.ktor", "ktor-network").versionRef("ktor")
            library("ktor-serialization-kotlinx-json", "io.ktor", "ktor-serialization-kotlinx-json").versionRef("ktor")

            library("ktor-server-cio", "io.ktor", "ktor-server-cio").versionRef("ktor")
            library("ktor-server-websockets", "io.ktor", "ktor-server-websockets").versionRef("ktor")
            library("ktor-server-content-negotiation", "io.ktor", "ktor-server-content-negotiation").versionRef("ktor")
            library("ktor-server-html-builder", "io.ktor", "ktor-server-html-builder").versionRef("ktor")
            library("ktor-server-status-pages", "io.ktor", "ktor-server-status-pages").versionRef("ktor")
        }
    }
}

include(
    ":simulation-kt",
    ":controls-core",
    ":controls-ports-ktor",
    ":controls-serial",
    ":controls-pi",
    ":controls-server",
    ":controls-opcua",
    ":controls-modbus",
    ":controls-plc4x",
//    ":controls-mongo",
    ":controls-storage",
    ":controls-storage:controls-xodus",
    ":controls-constructor",
    ":controls-visualisation-compose",
    ":controls-vision",
    ":controls-jupyter",
    ":magix",
    ":magix:magix-api",
    ":magix:magix-utils",
    ":magix:magix-server",
    ":magix:magix-rsocket",
    ":magix:magix-java-endpoint",
    ":magix:magix-zmq",
    ":magix:magix-rabbit",
    ":magix:magix-mqtt",
    ":magix:magix-storage",
    ":magix:magix-storage:magix-storage-xodus",
    ":controls-magix",
    ":demo:all-things",
    ":demo:many-devices",
    ":demo:magix-demo",
    ":demo:car",
    ":demo:motors",
    ":demo:echo",
    ":demo:mks-pdr900",
    ":demo:constructor",
    ":demo:device-collective",
    ":demo:thermo",
//    ":demo:thermo:thermo-desktop"
)
