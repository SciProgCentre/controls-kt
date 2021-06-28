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
    implementation(projects.controlsCore)
    //implementation(projects.controlsServer)
    implementation(projects.magix.magixServer)
    implementation(projects.controlsMagixClient)
    implementation(projects.magix.magixRsocket)
    implementation("no.tornado:tornadofx:1.7.20")
    implementation("space.kscience:plotlykt-server:0.4.2")
    implementation("com.github.Ricky12Awesome:json-schema-serialization:0.6.6")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = freeCompilerArgs + "-Xjvm-default=all"
    }
}

javafx{
    version = "14"
    modules("javafx.controls")
}

application{
    mainClass.set("ru.mipt.npm.controls.demo.DemoControllerViewKt")
}