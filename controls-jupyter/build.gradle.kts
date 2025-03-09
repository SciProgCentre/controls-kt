plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

kscience {
    fullStack("js/controls-jupyter.js")
    useKtor()
    useContextReceivers()
    jupyterLibrary("space.kscience.controls.jupyter.ControlsJupyter")
    dependencies {
        implementation(projects.controlsVision)
        implementation(libs.visionforge.jupiter)
    }
    jvmMain {
        implementation(spclibs.logback.classic)
    }
}