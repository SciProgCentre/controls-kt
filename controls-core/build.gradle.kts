plugins {
    id("ru.mipt.npm.mpp")
    id("ru.mipt.npm.publish")
}

val dataforgeVersion: String by rootProject.extra

kscience {
    useCoroutines("1.4.1")
    useSerialization{
        json()
    }
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