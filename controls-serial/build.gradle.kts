import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = "Implementation of direct serial port communication with JSerialComm"

kscience {
    jvm()
    jvmMain {
        api(project(":controls-core"))
        implementation(libs.jSerialComm)
    }
}

readme{
    maturity = Maturity.EXPERIMENTAL
}