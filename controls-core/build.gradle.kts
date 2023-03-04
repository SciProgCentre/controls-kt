plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

val dataforgeVersion: String by rootProject.extra

kscience {
    jvm()
    js()
    native()
    useCoroutines()
    useSerialization{
        json()
    }
    dependencies {
        api("space.kscience:dataforge-io:$dataforgeVersion")
        api(npmlibs.kotlinx.datetime)
    }
}
