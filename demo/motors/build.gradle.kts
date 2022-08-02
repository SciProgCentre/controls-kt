plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
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
    useFx(space.kscience.gradle.FXModule.CONTROLS, configuration = space.kscience.gradle.DependencyConfiguration.IMPLEMENTATION)
}

val ktorVersion: String by rootProject.extra
val dataforgeVersion: String by extra

dependencies {
    implementation(project(":controls-tcp"))
    implementation(project(":controls-magix-client"))
    implementation("no.tornado:tornadofx:1.7.20")
}
