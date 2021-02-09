plugins {
    java
    id("ru.mipt.npm.jvm")
    id("ru.mipt.npm.publish")
}

dependencies {
    implementation(project(":magix:magix-service"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9:${ru.mipt.npm.gradle.KScienceVersions.coroutinesVersion}")
}
