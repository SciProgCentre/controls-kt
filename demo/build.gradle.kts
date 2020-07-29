plugins {
    kotlin("jvm") version "1.3.72"
    id("org.openjfx.javafxplugin") version "0.0.9"
    application
}

val plotlyVersion by extra("0.2.0-dev-13")

repositories{
    jcenter()
    maven("https://kotlin.bintray.com/kotlinx")
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
    maven("https://dl.bintray.com/mipt-npm/dataforge")
    maven("https://dl.bintray.com/mipt-npm/scientifik")
    maven("https://dl.bintray.com/mipt-npm/dev")
}

dependencies{
    implementation(project(":dataforge-device-core"))
    implementation(project(":dataforge-device-server"))
    implementation(project(":dataforge-device-client"))
    implementation("no.tornado:tornadofx:1.7.20")
    implementation(kotlin("stdlib-jdk8"))
    implementation("scientifik:plotlykt-server:$plotlyVersion")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}

javafx{
    version = "14"
    modules("javafx.controls")
}

application{
    mainClassName = "hep.dataforge.control.demo.DemoControllerViewKt"
}