plugins {
    kotlin("jvm")
    id("org.openjfx.javafxplugin") version "0.0.9"
    application
}


repositories{
    mavenLocal()
    jcenter()
    maven("https://kotlin.bintray.com/kotlinx")
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
    maven("https://dl.bintray.com/mipt-npm/dataforge")
    maven("https://dl.bintray.com/mipt-npm/scientifik")
    maven("https://dl.bintray.com/mipt-npm/kscience")
    maven("https://dl.bintray.com/mipt-npm/dev")
}

dependencies{
    implementation(project(":dataforge-device-core"))
    implementation(project(":dataforge-device-server"))
    implementation(project(":dataforge-magix-client"))
    implementation("no.tornado:tornadofx:1.7.20")
    implementation(kotlin("stdlib-jdk8"))
    implementation("kscience.plotlykt:plotlykt-server:0.3.0-dev-2")
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