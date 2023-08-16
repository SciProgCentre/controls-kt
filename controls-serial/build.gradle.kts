plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

dependencies{
    api(project(":controls-core"))
    implementation("com.fazecast:jSerialComm:2.10.3")
}