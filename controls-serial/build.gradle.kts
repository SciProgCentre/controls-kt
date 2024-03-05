import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = "Implementation of direct serial port communication with JSerialComm"

dependencies{
    api(project(":controls-core"))
    implementation("com.fazecast:jSerialComm:2.10.4")
}

readme{
    maturity = Maturity.EXPERIMENTAL
}