plugins {
    kotlin("jvm")
    id("org.openjfx.javafxplugin") version "0.0.13"
    application
}


repositories {
    mavenCentral()
    maven("https://repo.kotlin.link")
}

val ktorVersion: String by rootProject.extra
val rsocketVersion: String by rootProject.extra

dependencies {
    implementation(projects.controlsCore)
    //implementation(projects.controlsServer)
    implementation(projects.magix.magixServer)
    implementation(projects.controlsMagixClient)
    implementation(projects.magix.magixRsocket)
    implementation(projects.magix.magixZmq)
    implementation(projects.controlsOpcua)

    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("no.tornado:tornadofx:1.7.20")
    implementation("space.kscience:plotlykt-server:0.5.3-dev-1")
//    implementation("com.github.Ricky12Awesome:json-schema-serialization:0.6.6")
    implementation("ch.qos.logback:logback-classic:1.2.11")
}

kotlin{
    jvmToolchain(11)
}


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all", "-Xopt-in=kotlin.RequiresOptIn")
    }
}

javafx {
    version = "17"
    modules("javafx.controls")
}

application {
    mainClass.set("space.kscience.controls.demo.DemoControllerViewKt")
}