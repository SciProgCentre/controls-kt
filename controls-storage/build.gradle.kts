plugins {
    id("ru.mipt.npm.gradle.mpp")
    `maven-publish`
}

val dataforgeVersion: String by rootProject.extra

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.controlsCore)
            }
        }

        jvmMain {
            dependencies {
                implementation(projects.magix.magixApi)
                implementation(projects.controlsMagixClient)
                implementation(projects.magix.magixServer)
            }
        }
    }
}
