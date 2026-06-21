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
    implementation(projects.magix.magixRsocket)
    implementation(projects.magix.magixZmq)
    implementation(spclibs.ktor.client.cio)

    implementation(libs.logback.classic)
}
kotlin{
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all")
    }
}

application {
    mainClass.set("space.kscience.controls.demo.echo.MainKt")
}