plugins {
    kotlin("jvm") version "1.3.72"
}

val plotlyVersion: String by rootProject.extra

repositories{
    jcenter()
    maven("https://kotlin.bintray.com/kotlinx")
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
    maven("https://dl.bintray.com/mipt-npm/dataforge")
    maven("https://dl.bintray.com/mipt-npm/scientifik")
    maven("https://dl.bintray.com/mipt-npm/dev")
}

dependencies{
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":dataforge-control-core"))
    implementation("scientifik:plotlykt-server:$plotlyVersion")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}