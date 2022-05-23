plugins {
    java
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

dependencies {
    implementation(project(":magix:magix-rsocket"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9:${ru.mipt.npm.gradle.KScienceVersions.coroutinesVersion}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
