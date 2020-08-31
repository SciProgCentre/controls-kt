plugins {
    id("kscience.mpp")
    id("kscience.publish")
}

val dataforgeVersion: String by rootProject.extra

kscience {
    useCoroutines()
    useSerialization()
}

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