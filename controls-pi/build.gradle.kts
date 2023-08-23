plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = """
    Utils to work with controls-kt on Raspberry pi
""".trimIndent()

dependencies{
    api(project(":controls-core"))
    api("com.pi4j:pi4j-ktx:2.4.0") // Kotlin DSL
    api("com.pi4j:pi4j-core:2.3.0")
    api("com.pi4j:pi4j-plugin-raspberrypi:2.3.0")
    api("com.pi4j:pi4j-plugin-pigpio:2.3.0")
}