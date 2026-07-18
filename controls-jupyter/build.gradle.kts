import org.jetbrains.kotlinx.jupyter.api.plugin.tasks.JupyterApiResourcesTask

plugins {
    id("space.kscience.gradle.mpp")
    alias(spclibs.plugins.kotlin.jupyter.api)
    `maven-publish`
}

kscience {
    fullStack("js/controls-jupyter.js")

    dependencies {
        implementation(projects.controlsVision)
        implementation(libs.visionforge.jupiter)
    }

    jvmMain {
        implementation(spclibs.logback.classic)
    }
}

tasks.withType<JupyterApiResourcesTask> {
    libraryProducers = listOf("space.kscience.controls.jupyter.ControlsJupyter")
}