plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

val visionforgeVersion: String by rootProject.extra

kscience {
    fullStack("js/controls-jupyter.js")
    useKtor()
    useContextReceivers()
    jupyterLibrary("space.kscience.controls.jupyter.ControlsJupyter")
    dependencies {
        implementation(projects.controlsVision)
        implementation("space.kscience:visionforge-jupyter:$visionforgeVersion")
    }
    jvmMain {
        implementation(spclibs.logback.classic)
    }
}