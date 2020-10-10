import ru.mipt.npm.gradle.useFx

plugins {
    id("ru.mipt.npm.jvm")
    id("ru.mipt.npm.publish")
}

//TODO to be moved to a separate project

kotlin{
    explicitApi = null
    useFx(ru.mipt.npm.gradle.FXModule.CONTROLS)
}

val ktorVersion: String by rootProject.extra

dependencies {
    implementation(project(":dataforge-device-core"))
    implementation(project(":dataforge-magix-client"))
    implementation("no.tornado:tornadofx:1.7.20")
}
