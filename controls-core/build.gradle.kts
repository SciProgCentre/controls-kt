plugins {
    id("ru.mipt.npm.gradle.mpp")
    `maven-publish`
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
                api("space.kscience:dataforge-io:$dataforgeVersion")
                api(npm.kotlinx.datetime)
            }
        }
    }
}