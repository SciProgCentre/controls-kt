plugins {
    id("space.kscience.gradle.jvm")
    alias(spclibs.plugins.compose.compiler)
    alias(spclibs.plugins.compose.jb)
}

kotlin {
    explicitApi = null
}

val ktorVersion: String by rootProject.extra
val dataforgeVersion: String by extra

kscience {
}

dependencies {
    implementation(projects.controlsPortsKtor)
    implementation(projects.controlsConstructor)
    implementation(projects.controlsMagix)
    implementation(projects.controlsVisualisationCompose)

    implementation("org.jetbrains.compose.runtime:runtime")
    implementation(compose.desktop.currentOs)
    implementation(spclibs.logback.classic)
}

compose {
    desktop {
        application {
            mainClass = "ru.mipt.npm.devices.pimotionmaster.PiMotionMasterAppKt"
        }
    }
}
