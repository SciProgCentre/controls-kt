import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
   A combined Magix event loop server with web server for visualization.
""".trimIndent()


kscience {
    jvm()
    dependencies {
        implementation(projects.controlsCore)
        implementation(projects.controlsPortsKtor)
        implementation(projects.magix.magixServer)
        implementation(spclibs.ktor.server.cio)
        implementation(spclibs.ktor.server.websockets)
        implementation(spclibs.ktor.server.content.negotiation)
        implementation(spclibs.ktor.serialization.kotlinx.json)
        implementation(spclibs.ktor.server.html.builder)
        implementation(spclibs.ktor.server.status.pages)
    }
}

readme{
    maturity = Maturity.PROTOTYPE
}