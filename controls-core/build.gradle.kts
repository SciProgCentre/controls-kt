plugins {
    id("space.kscience.gradle.mpp")
    id("space.kscience.gradle.native")
    `maven-publish`
}

val dataforgeVersion: String by rootProject.extra

kscience {
    useCoroutines()
    useSerialization{
        json()
    }
}

kotlin {
    sourceSets {
        commonMain{
            dependencies {
                api("space.kscience:dataforge-io:$dataforgeVersion")
                api(npmlibs.kotlinx.datetime)
            }
        }
    }
}