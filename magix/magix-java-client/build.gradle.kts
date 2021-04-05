plugins {
    java
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

dependencies {
    implementation(project(":magix:magix-service"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9:${ru.mipt.npm.gradle.KScienceVersions.coroutinesVersion}")
}
