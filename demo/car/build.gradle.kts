plugins {
    kotlin("jvm")
    id("org.openjfx.javafxplugin")
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
    implementation(projects.magix.magixApi)
    implementation(projects.magix.magixServer)
    implementation(projects.magix.magixRsocket)
    implementation(projects.controlsMagixClient)

    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.1")
    implementation("no.tornado:tornadofx:1.7.20")
    implementation("space.kscience:plotlykt-server:0.5.0-dev-1")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("org.jetbrains.xodus:xodus-entity-store:1.3.232")
    implementation("org.jetbrains.xodus:xodus-environment:1.3.232")
    implementation("org.jetbrains.xodus:xodus-vfs:1.3.232")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all", "-Xopt-in=kotlin.RequiresOptIn")
    }
}

javafx {
    version = "14"
    modules("javafx.controls")
}

//application {
//    mainClass.set("ru.mipt.npm.controls.demo.DemoControllerViewKt")
//}