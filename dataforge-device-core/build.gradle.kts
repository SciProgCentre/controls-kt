import scientifik.useCoroutines
import scientifik.useSerialization

plugins {
    id("scientifik.mpp")
    id("scientifik.publish")
}

val dataforgeVersion: String by rootProject.extra

useCoroutines(version = "1.3.7")
useSerialization()

kotlin {
    sourceSets {
        commonMain{
            dependencies {
                api("hep.dataforge:dataforge-io:$dataforgeVersion")
                //implementation("org.jetbrains.kotlinx:atomicfu-common:0.14.3")
                //api("io.github.microutils:kotlin-logging-common:1.8.3")
            }
        }
        jvmMain{
            dependencies{
                //api("io.github.microutils:kotlin-logging:1.8.3")
            }
        }
        jsMain{
            dependencies{
                //api("io.github.microutils:kotlin-logging-js:1.8.3")
            }
        }
    }
}