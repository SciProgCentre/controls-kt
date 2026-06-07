import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    A plugin for Controls-kt device server on top of plc4x library
""".trimIndent()

kscience {
    jvm()
    jvmMain {
        api(projects.controlsCore)
        api(libs.plc4j.spi)
    }
}

readme {
    maturity = Maturity.EXPERIMENTAL
}