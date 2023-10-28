plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Dashboard and visualization extensions for devices
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
