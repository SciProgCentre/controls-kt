plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}


dependencies {
    implementation(projects.controlsCore)
    implementation(projects.magix.magixApi)
    implementation("org.jetbrains.xodus:xodus-entity-store:1.3.232")
    implementation("org.jetbrains.xodus:xodus-environment:1.3.232")
    implementation("org.jetbrains.xodus:xodus-vfs:1.3.232")
}