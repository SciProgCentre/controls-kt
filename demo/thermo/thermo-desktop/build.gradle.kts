plugins {
    id("space.kscience.gradle.mpp")
    alias(spclibs.plugins.compose.compiler)
    alias(spclibs.plugins.compose.jb)
}

kotlin {
    explicitApi = null
}

kscience {
    jvm()

    useSerialization {
        json()
    }

    jvmMain {
        implementation(projects.demo.thermo)

        //compose desktop dependencies
        implementation(projects.controlsVisualisationCompose)
        implementation(compose.runtime)
        implementation(compose.desktop.currentOs)
        implementation(compose.material3)
    }
}

compose {
    desktop {
        application {
            mainClass = "center.sciprog.controls.demo.thermo.ComposePanelKt"

            nativeDistributions {
                packageName = "ControlsThermoSensor"
                packageVersion = "1.0.0"
                windows {
                    includeAllModules = true
                }
            }
        }
    }
}
