plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    A low-code constructor for composite devices simulation
""".trimIndent()

kscience{
    jvm()
    js()
    dependencies {
        api(projects.controlsCore)
    }
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
