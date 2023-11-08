plugins {
    id("space.kscience.gradle.mpp")
}

val visionforgeVersion: String by rootProject.extra

kscience {
    fullStack("js/controls-jupyter.js")
    useKtor()
    useContextReceivers()
    jupyterLibrary()
    dependencies {
        implementation(projects.controlsVision)
        implementation("space.kscience:visionforge-jupyter:$visionforgeVersion")
    }
    jvmMain {
        implementation(spclibs.logback.classic)
    }
}