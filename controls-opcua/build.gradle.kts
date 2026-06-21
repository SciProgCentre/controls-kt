import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = """
    A client and server connectors for OPC-UA via Eclipse Milo
""".trimIndent()

val ktorVersion: String by rootProject.extra

dependencies {
    api(projects.controlsCore)
    api(spclibs.kotlinx.coroutines.core)
    api(spclibs.kotlinx.coroutines.jdk8)

    api(libs.milo.client)
    api(libs.milo.dtd.core)
    api(libs.milo.server)

    testImplementation(spclibs.kotlinx.coroutines.test)
    testImplementation(spclibs.logback.classic)
}

readme {
    maturity = Maturity.EXPERIMENTAL

    feature("opcuaClient", ref = "src/main/kotlin/space/kscience/controls/opcua/client") {
        """
            Connect a Controls-kt as a client to OPC UA server
        """.trimIndent()
    }

    feature("opcuaServer", ref = "src/main/kotlin/space/kscience/controls/opcua/server") {
        """
            Create an OPC UA server on top of Controls-kt device (or device hub)
        """.trimIndent()
    }
}
