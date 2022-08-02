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
    implementation(projects.magix.magixRsocket)
    implementation(projects.magix.magixZmq)
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:1.2.11")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all", "-Xopt-in=kotlin.RequiresOptIn")
    }
}

application {
    mainClass.set("space.kscience.controls.demo.echo.MainKt")
}