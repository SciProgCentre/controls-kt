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

    jsMain{
        //FIXME remove after VisionForge 0.5
        api("org.jetbrains.kotlin-wrappers:kotlin-extensions:1.0.1-pre.823")
    }
    jvmMain {
        implementation(spclibs.logback.classic)
    }
}