plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

val xodusVersion = "1.3.232"

dependencies {
    implementation(projects.controlsCore)
    implementation(projects.magix.magixApi)
    implementation(projects.controlsMagixClient)
    implementation(projects.magix.magixServer)
    implementation(projects.xodusSerialization)
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-environment:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-vfs:$xodusVersion")
}