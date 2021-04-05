plugins {
    kotlin("jvm")
    id("org.openjfx.javafxplugin") version "0.0.9"
    application
}


repositories{
    mavenCentral()
    jcenter()
    maven("https://repo.kotlin.link")
    maven("https://kotlin.bintray.com/kotlinx")
}

dependencies{
    implementation(project(":controls-core"))
    implementation(project(":controls-server"))
    implementation(project(":controls-magix-client"))
    implementation("no.tornado:tornadofx:1.7.20")
    implementation("space.kscience:plotlykt-server:0.4.0-dev-2")
    implementation("com.github.Ricky12Awesome:json-schema-serialization:0.6.6")
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
    mainClass.set("space.kscience.dataforge.control.demo.DemoControllerViewKt")
}