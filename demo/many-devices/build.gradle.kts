plugins {
    kotlin("jvm")
    application
}


repositories {
    mavenCentral()
    maven("https://repo.kotlin.link")
}

val ktorVersion: String by rootProject.extra
val rsocketVersion: String by rootProject.extra

dependencies {
    implementation(projects.magix.magixServer)
    implementation(projects.controlsMagixClient)
    implementation(projects.magix.magixRsocket)
    implementation(projects.magix.magixZmq)

    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("space.kscience:plotlykt-server:0.5.3")
    implementation(spclibs.logback.classic)
}

kotlin{
    jvmToolchain(11)
}


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all", "-Xopt-in=kotlin.RequiresOptIn")
    }
}


application {
    mainClass.set("space.kscience.controls.demo.MassDeviceKt")
}