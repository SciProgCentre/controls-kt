plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

dependencies{
    api(project(":controls-core"))
    implementation("org.scream3r:jssc:2.8.0")
}