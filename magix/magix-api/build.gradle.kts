plugins {
    id("ru.mipt.npm.mpp")
    id("ru.mipt.npm.publish")
}

kscience {
    useSerialization{
        json()
    }
}

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String by rootProject.extra

