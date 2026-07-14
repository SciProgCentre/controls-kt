import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    alias(spclibs.plugins.ktor)
    `maven-publish`
}

description = """
   A combined Magix event loop server with web server for visualization.
""".trimIndent()


kscience {
    jvm()
    useSerialization()
    dependencies {
        implementation(projects.controlsCore)
        implementation(projects.controlsPortsKtor)
        implementation(projects.magix.magixServer)
        implementation(project.dependencies.platform(spclibs.ktor.bom))
        implementation("io.ktor:ktor-server-core")
        implementation("io.ktor:ktor-server-websockets")
        implementation("io.ktor:ktor-server-content-negotiation")
        implementation("io.ktor:ktor-serialization-kotlinx-json")
        implementation("io.ktor:ktor-server-html-builder")
        implementation("io.ktor:ktor-server-status-pages")
        implementation("io.ktor:ktor-server-routing-openapi")
        implementation("io.ktor:ktor-server-openapi")
    }

    jvmTest {
        dependencies {
            implementation("io.ktor:ktor-server-test-host")
            implementation("io.ktor:ktor-client-content-negotiation")
            implementation("io.ktor:ktor-client-websockets")
        }
    }
}

readme {
    maturity = Maturity.PROTOTYPE
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
    }
}
