import scientifik.useCoroutines

plugins {
    id("scientifik.mpp")
    id("scientifik.publish")
    id("kotlinx-atomicfu") version "0.14.3"
}

val dataforgeVersion: String by rootProject.extra

useCoroutines(version = "1.3.7")

kotlin {
    sourceSets {
        commonMain{
            dependencies {
                api("hep.dataforge:dataforge-io:$dataforgeVersion")
                //implementation("org.jetbrains.kotlinx:atomicfu-common:0.14.3")
            }
        }
    }
}

atomicfu {
    variant = "VH"
}