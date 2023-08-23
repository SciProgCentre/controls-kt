import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = """
    Implementation of byte ports on top os ktor-io asynchronous API
""".trimIndent()

val ktorVersion: String by rootProject.extra

dependencies {
    api(projects.controlsCore)
    api("io.ktor:ktor-network:$ktorVersion")
}

readme{
    maturity = Maturity.PROTOTYPE
}
