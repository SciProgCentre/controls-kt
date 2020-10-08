plugins {
    id("ru.mipt.npm.jvm")
    id("ru.mipt.npm.publish")
}

//TODO to be moved to a separate project

kotlin{
    explicitApi = null
}

val ktorVersion: String by rootProject.extra

dependencies {
    implementation(project(":dataforge-device-core"))
    implementation(project(":dataforge-magix-client"))
}
