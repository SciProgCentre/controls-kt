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
        implementation(projects.controlsCore)
        implementation(projects.controlsConstructor)
        implementation(projects.controlsVisualisationCompose)
        implementation(projects.controlsModbus)
        implementation(projects.controlsOpcua)

        implementation(compose.runtime)
        implementation(compose.desktop.currentOs)
        implementation(compose.material3)
        implementation(spclibs.logback.classic)
    }
}

compose{
    desktop{
        application{
            mainClass = "center.sciprog.controls.demo.thermo.PanelKt"
        }
    }
}
