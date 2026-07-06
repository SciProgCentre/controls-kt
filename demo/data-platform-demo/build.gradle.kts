plugins {
    kotlin("jvm")
    alias(spclibs.plugins.compose.compiler)
    alias(spclibs.plugins.compose.jb)
}


repositories {
    mavenCentral()
    maven("https://repo.kotlin.link")
}

dependencies {
    implementation(projects.controlsTable)
    implementation(projects.controlsVisualisationCompose)

//    implementation(libs.plotlykt.server)

    implementation(compose.runtime)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

//    implementation(projects.controlsServer)
//    implementation("io.ktor:ktor-server-cio")

    implementation(spclibs.logback.classic)
}

kotlin{
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all", "-Xopt-in=kotlin.RequiresOptIn")
    }
}

compose{
    desktop{
        application{
            mainClass = "space.kscience.controls.demo.MainKt"
        }
    }
}
//
//
//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//    kotlinOptions {
//        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all", "-Xopt-in=kotlin.RequiresOptIn")
//    }
//}
//
//javafx {
//    version = "17"
//    modules("javafx.controls")
//}
//
//application {
//    mainClass.set("space.kscience.controls.demo.DemoControllerViewKt")
//}