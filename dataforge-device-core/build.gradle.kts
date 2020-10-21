plugins {
    id("ru.mipt.npm.mpp")
    id("ru.mipt.npm.publish")
}

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String by rootProject.extra

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
    }
}