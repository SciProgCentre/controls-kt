plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Dashboard and visualization extensions for devices
""".trimIndent()

kscience {
    fullStack("js/controls-vision.js")
    useSerialization()
    commonMain {
        api(projects.controlsCore)
        api(projects.controlsConstructor)
        api(libs.plotlykt.core)
        api(libs.visionforge.markdown)
//        api("space.kscience:tables-kt:0.2.1")
//        api("space.kscience:visionforge-tables:$visionforgeVersion")
    }

    jvmMain{
        api(libs.visionforge.server)
        api("io.ktor:ktor-server-cio")
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}