import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Implementation of byte ports on top os ktor-io asynchronous API
""".trimIndent()

kscience {
    jvm()
    jvmMain {
        api(projects.controlsCore)
        api(spclibs.ktor.network)
    }
}

readme{
    maturity = Maturity.PROTOTYPE
}
