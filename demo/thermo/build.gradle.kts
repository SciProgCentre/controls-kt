plugins {
    id("space.kscience.gradle.mpp")
    alias(spclibs.plugins.compose.compiler)
    alias(spclibs.plugins.compose.jb)
}

kotlin {
    explicitApi = null
}

kscience {
    fullStack(
        bundleName = "js/thermo-vision.js",
        jvmConfig = {
            binaries {
                executable {
                    mainClass = "center.sciprog.controls.demo.thermo.VisionPanelKt"
                }
            }
        }
    )

    useSerialization {
        json()
    }

    commonMain {
        implementation(projects.controlsCore)
        implementation(projects.controlsConstructor)

        //web UI dependencies
        implementation(libs.plotlykt.core)
    }


    jvmMain {

        implementation(projects.controlsModbus)
        implementation(projects.controlsOpcua)

        //compose desktop dependencies
        implementation(projects.controlsVisualisationCompose)
        implementation(compose.runtime)
        implementation(compose.desktop.currentOs)
        implementation(compose.material3)

        implementation(libs.visionforge.server)
        api(spclibs.ktor.server.cio)

        implementation(spclibs.logback.classic)
    }

    jsMain{
        implementation(libs.visionforge.compose.html)
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
