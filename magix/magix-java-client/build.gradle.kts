import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import space.kscience.gradle.KScienceVersions

plugins {
    java
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

dependencies {
    implementation(project(":magix:magix-rsocket"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9:${KScienceVersions.coroutinesVersion}")
}

java {
    sourceCompatibility = KScienceVersions.JVM_TARGET
    targetCompatibility = KScienceVersions.JVM_TARGET
}


//FIXME https://youtrack.jetbrains.com/issue/KT-52815/Compiler-option-Xjdk-release-fails-to-compile-mixed-projects
tasks.withType<KotlinCompile>{
    kotlinOptions {
        freeCompilerArgs -= "-Xjdk-release=11"
    }
}