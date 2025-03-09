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
    implementation(projects.controlsCore)
    //implementation(projects.controlsServer)
    implementation(projects.magix.magixServer)
    implementation(projects.controlsMagix)
    implementation(projects.magix.magixRsocket)
    implementation(projects.magix.magixZmq)
    implementation(projects.controlsOpcua)

    implementation(spclibs.ktor.client.cio)
    implementation(libs.plotlykt.server)
//    implementation("com.github.Ricky12Awesome:json-schema-serialization:0.6.6")

    implementation(compose.runtime)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
//    implementation("org.pushing-pixels:aurora-window:1.3.0")
//    implementation("org.pushing-pixels:aurora-component:1.3.0")
//    implementation("org.pushing-pixels:aurora-theming:1.3.0")

    implementation(spclibs.logback.classic)
}

kotlin{
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all", "-Xopt-in=kotlin.RequiresOptIn")
    }
}

compose{
    desktop{
        application{
            mainClass = "space.kscience.controls.demo.DemoControllerViewKt"
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