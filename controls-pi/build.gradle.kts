plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Utils to work with controls-kt on Raspberry pi
""".trimIndent()

kscience {
    jvm()


    jvmMain {
        api(projects.controlsCore)
        api(projects.controlsSerial)
        api(libs.pi4j.core)
        api(libs.pi4j.plugin.raspberrypi)
        api(libs.pi4j.plugin.pigpio)
    }
}