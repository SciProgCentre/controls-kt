plugins {
    id("scientifik.mpp")
    id("scientifik.publish")
}

val dataforgeVersion: String by rootProject.extra

kotlin {

    sourceSets {
        commonMain{
            dependencies {
                api("hep.dataforge:dataforge-meta:$dataforgeVersion")
            }
        }
    }
}