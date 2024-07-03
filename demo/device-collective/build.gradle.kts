import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    id("space.kscience.gradle.mpp")
    alias(spclibs.plugins.compose.compiler)
    alias(spclibs.plugins.compose.jb)
}

kscience {
    jvm()
    useSerialization()
    useContextReceivers()
    commonMain {
        implementation(projects.controlsVisualisationCompose)
        implementation(projects.controlsConstructor)
        implementation(projects.magix.magixServer)
        implementation(projects.magix.magixRsocket)
        implementation(projects.controlsMagix)
    }
    jvmMain {
//        implementation("io.ktor:ktor-server-cio")
        implementation(spclibs.logback.classic)
        implementation(libs.sciprog.maps.compose)
    }
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

kotlin.explicitApi = ExplicitApiMode.Disabled


compose.desktop {
    application {
        mainClass = "space.kscience.controls.demo.collective.MainKt"
    }
}