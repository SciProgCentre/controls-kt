plugins {
    id("space.kscience.gradle.jvm")
    application
    id("org.openjfx.javafxplugin")
}

//TODO to be moved to a separate project

javafx {
    version = "17"
    modules = listOf("javafx.controls")
}

application{
    mainClass.set("ru.mipt.npm.devices.pimotionmaster.PiMotionMasterAppKt")
}

kotlin{
    explicitApi = null
}

val ktorVersion: String by rootProject.extra
val dataforgeVersion: String by extra

dependencies {
    implementation(project(":controls-tcp"))
    implementation(project(":controls-magix-client"))
    implementation("no.tornado:tornadofx:1.7.20")
}
