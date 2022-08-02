plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

val dataforgeVersion: String by rootProject.extra

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.controlsCore)
            }
        }

        jvmMain {
            dependencies {
                api(projects.magix.magixApi)
                api(projects.controlsMagixClient)
                api(projects.magix.magixServer)
            }
        }
    }
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
