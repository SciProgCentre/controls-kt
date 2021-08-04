plugins {
    id("ru.mipt.npm.gradle.jvm")
}

val ktorVersion: String by rootProject.extra

val miloVersion: String = "0.6.3"

dependencies {
    api(project(":controls-core"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${ru.mipt.npm.gradle.KScienceVersions.coroutinesVersion}")
    implementation("org.eclipse.milo:sdk-client:$miloVersion")
    implementation("org.eclipse.milo:bsd-parser:$miloVersion")
    implementation("org.eclipse.milo:dictionary-reader:$miloVersion")
}
