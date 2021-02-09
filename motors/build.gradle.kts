plugins {
    id("ru.mipt.npm.jvm")
    id("ru.mipt.npm.publish")
    application
}

//TODO to be moved to a separate project

application{
    mainClass.set("ru.mipt.npm.devices.pimotionmaster.PiMotionMasterAppKt")
}

kotlin{
    explicitApi = null
}

kscience{
    useFx(ru.mipt.npm.gradle.FXModule.CONTROLS, configuration = ru.mipt.npm.gradle.DependencyConfiguration.IMPLEMENTATION)
}

val ktorVersion: String by rootProject.extra

dependencies {
    implementation(project(":controls-tcp"))
    implementation(project(":controls-magix-client"))
    implementation("no.tornado:tornadofx:1.7.20")
}
