import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = """
    A client and server connectors for OPC-UA via Eclipse Milo
""".trimIndent()

val ktorVersion: String by rootProject.extra

val miloVersion: String = "0.6.10"

dependencies {
    api(projects.controlsCore)
    api(spclibs.kotlinx.coroutines.jdk8)

    api("org.eclipse.milo:sdk-client:$miloVersion")
    api("org.eclipse.milo:bsd-parser:$miloVersion")
    api("org.eclipse.milo:sdk-server:$miloVersion")

    testImplementation(spclibs.kotlinx.coroutines.test)
}

readme{
    maturity = Maturity.EXPERIMENTAL

    feature("opcuaClient", ref = "src/main/kotlin/space/kscience/controls/opcua/client"){
        """
            Connect a Controls-kt as a client to OPC UA server
        """.trimIndent()
    }

    feature("opcuaServer", ref = "src/main/kotlin/space/kscience/controls/opcua/server"){
        """
            Create an OPC UA server on top of Controls-kt device (or device hub)
        """.trimIndent()
    }
}
