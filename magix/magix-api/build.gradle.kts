plugins {
    id("ru.mipt.npm.mpp")
    id("ru.mipt.npm.publish")
}

kscience {
    useSerialization()
    useCoroutines("1.4.0", configuration = ru.mipt.npm.gradle.DependencyConfiguration.API)
}

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String by rootProject.extra

