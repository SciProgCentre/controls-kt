plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = """
    Utils to work with controls-kt on Raspberry pi
""".trimIndent()

val pi4jVerstion = "2.3.0"

dependencies{
    api(project(":controls-core"))
    api("com.pi4j:pi4j-ktx:2.4.0") // Kotlin DSL
    api("com.pi4j:pi4j-core:$pi4jVerstion")
    api("com.pi4j:pi4j-plugin-raspberrypi:$pi4jVerstion")
    api("com.pi4j:pi4j-plugin-pigpio:$pi4jVerstion")
}