plugins {
    kotlin("jvm")
    application
}


repositories {
    mavenCentral()
    maven("https://repo.kotlin.link")
}

dependencies {
    implementation(projects.magix.magixServer)
    implementation(projects.controlsMagix)
    implementation(projects.magix.magixRsocket)
    implementation(projects.magix.magixZmq)

    implementation("io.ktor:ktor-client-cio")
    implementation(libs.plotlykt.server)
    implementation(spclibs.logback.classic)
}

kotlin{
    jvmToolchain(21)
}


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all", "-Xcontext-parameters")
    }
}


application {
    mainClass.set("space.kscience.controls.demo.MassDeviceKt")
}