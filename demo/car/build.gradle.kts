plugins {
    kotlin("jvm")
    id("org.openjfx.javafxplugin") version "0.1.0"
    application
}


repositories {
    mavenCentral()
    maven("https://repo.kotlin.link")
}

dependencies {
    implementation(projects.controlsCore)
    implementation(projects.magix.magixApi)
    implementation(projects.magix.magixServer)
    implementation(projects.magix.magixRsocket)
    implementation(projects.magix.magixZmq)
    implementation(projects.controlsMagix)
    implementation(projects.controlsStorage.controlsXodus)
    implementation(projects.magix.magixStorage.magixStorageXodus)
//    implementation(projects.controlsMongo)

    implementation(spclibs.ktor.client.cio)
    implementation(spclibs.kotlinx.datetime)
    implementation(libs.tornadofx)
    implementation(libs.plotlykt.server)
    implementation(libs.logback.classic)
    implementation(libs.xodus.entity.store)
    implementation(libs.xodus.environment)
    implementation(libs.xodus.vfs)
//    implementation("org.litote.kmongo:kmongo-coroutine-serialization:4.4.0")
}

kotlin{
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all")
    }
}

javafx {
    version = "17"
    modules("javafx.controls")
}

application {
    mainClass.set("space.kscience.controls.demo.car.VirtualCarControllerKt")
}