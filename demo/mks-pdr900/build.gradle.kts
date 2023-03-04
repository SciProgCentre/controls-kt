plugins {
    id("space.kscience.gradle.jvm")
    application
}

//TODO to be moved to a separate project
//
//application{
//    mainClass.set("ru.mipt.npm.devices.pimotionmaster.PiMotionMasterAppKt")
//}

kotlin{
    explicitApi = null
}

val ktorVersion: String by rootProject.extra
val dataforgeVersion: String by extra

dependencies {
    implementation(projects.controlsKtorTcp)
}
