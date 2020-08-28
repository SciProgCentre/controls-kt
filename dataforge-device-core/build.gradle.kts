import scientifik.useCoroutines
import scientifik.useSerialization

plugins {
    id("scientifik.mpp")
    id("scientifik.publish")
}

val dataforgeVersion: String by rootProject.extra

useCoroutines()
useSerialization()

kotlin {
    sourceSets {
        commonMain{
            dependencies {
                api("hep.dataforge:dataforge-io:$dataforgeVersion")
                //implementation("org.jetbrains.kotlinx:atomicfu-common:0.14.3")
            }
        }
        jvmMain{
            dependencies{
                api("io.ktor:ktor-network:1.3.2")
            }
        }
        jsMain{
            dependencies{
            }
        }
    }
}