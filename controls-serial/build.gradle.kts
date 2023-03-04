plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

dependencies{
    api(project(":controls-core"))
    implementation("org.scream3r:jssc:2.8.0")
}