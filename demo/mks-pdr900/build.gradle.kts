plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
    maven("https://repo.kotlin.link")
}

kotlin {
    explicitApi = null
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all", "-Xopt-in=kotlin.RequiresOptIn", "-Xcontext-parameters")
    }
}

val ktorVersion: String by rootProject.extra
val dataforgeVersion: String by extra


dependencies {
    implementation(projects.controlsPortsKtor)
}
