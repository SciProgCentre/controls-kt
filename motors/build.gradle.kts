import ru.mipt.npm.gradle.useFx

plugins {
    id("ru.mipt.npm.jvm")
    id("ru.mipt.npm.publish")
    application
}

//TODO to be moved to a separate project

application{
    mainClassName = "ru.mipt.npm.devices.pimotionmaster.PiMotionMasterAppKt"
}

kotlin{
    explicitApi = null
    useFx(ru.mipt.npm.gradle.FXModule.CONTROLS, configuration = ru.mipt.npm.gradle.DependencyConfiguration.IMPLEMENTATION)
}

val ktorVersion: String by rootProject.extra

dependencies {
    implementation(project(":dataforge-device-tcp"))
    implementation(project(":dataforge-magix-client"))
    implementation("no.tornado:tornadofx:1.7.20")
}
