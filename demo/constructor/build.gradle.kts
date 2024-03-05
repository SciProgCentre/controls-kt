import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    id("space.kscience.gradle.mpp")
    id("org.jetbrains.compose") version "1.5.11"
}

kscience {
    jvm {
        withJava()
    }
    useKtor()
    useContextReceivers()
    dependencies {
        api(projects.controlsVision)
    }
    jvmMain {
        implementation("io.ktor:ktor-server-cio")
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