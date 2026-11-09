import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    id("space.kscience.gradle.mpp")
    alias(spclibs.plugins.compose.compiler)
    alias(libs.plugins.compose.jb)
}

kscience {
    jvm()
    useSerialization()
    commonMain {
        implementation(projects.controlsVisualisationCompose)
//        implementation(projects.controlsVision)
        implementation(projects.controlsConstructor)
        implementation(projects.controlsModels.controlsModelsMechanical)
        implementation(projects.controlsModels.controlsModelsFlow)
    }
    jvmMain {
//        implementation("io.ktor:ktor-server-cio")
        implementation(spclibs.logback.classic)
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

//application {
//    mainClass.set("space.kscience.controls.demo.constructor.MainKt")
//}

kotlin.explicitApi = ExplicitApiMode.Disabled


compose.desktop {
    application {
        mainClass = "space.kscience.controls.demo.constructor.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "PidConstructor"
            packageVersion = "1.0.0"
        }
    }
}