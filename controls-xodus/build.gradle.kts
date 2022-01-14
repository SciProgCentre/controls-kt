plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

val xodusVersion = "1.3.232"

dependencies {
    implementation(projects.xodusSerialization)
    implementation(projects.controlsStorage)
    implementation(projects.controlsCore)
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-environment:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-vfs:$xodusVersion")
}