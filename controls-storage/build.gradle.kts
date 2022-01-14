plugins {
    id("ru.mipt.npm.gradle.mpp")
    `maven-publish`
}

val dataforgeVersion: String by rootProject.extra
val kotlinx_io_version = "0.1.1"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.controlsCore)
                implementation("org.jetbrains.kotlinx:kotlinx-io:$kotlinx_io_version")
            }
        }

        jvmMain {
            dependencies {
                implementation(projects.magix.magixApi)
                implementation(projects.controlsMagixClient)
                implementation(projects.magix.magixServer)
                implementation("org.jetbrains.kotlinx:kotlinx-io-jvm:$kotlinx_io_version")
            }
        }
    }
}
