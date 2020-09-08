plugins {
    id("ru.mipt.npm.mpp")
    id("ru.mipt.npm.publish")
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