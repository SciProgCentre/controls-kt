import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
   A combined Magix event loop server with web server for visualization.
""".trimIndent()

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String by rootProject.extra


kscience {
    jvm()
    dependencies {
        implementation(projects.controlsCore)
        implementation(projects.controlsPortsKtor)
        implementation(projects.magix.magixServer)
        implementation("io.ktor:ktor-server-cio:$ktorVersion")
        implementation("io.ktor:ktor-server-websockets:$ktorVersion")
        implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
        implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
        implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    }
}

readme{
    maturity = Maturity.PROTOTYPE
}